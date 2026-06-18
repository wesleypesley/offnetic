package com.offnetic.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.offnetic.R
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.ui.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject

@AndroidEntryPoint
class IncomingCallService : Service() {

    @Inject lateinit var contactDao: ContactDao

    companion object {
        const val CHANNEL_ID = "offnetic_incoming_calls"
        const val NOTIFICATION_ID = 7000
        const val ACTION_START_RINGING = "com.offnetic.ACTION_START_RINGING"
        const val ACTION_STOP_RINGING = "com.offnetic.ACTION_STOP_RINGING"
        const val EXTRA_PEER_PUBLIC_KEY = "EXTRA_PEER_PUBLIC_KEY"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var timeoutJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isRinging = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RINGING -> {
                val peerKey = intent.getStringExtra(EXTRA_PEER_PUBLIC_KEY) ?: ""
                if (peerKey.isNotEmpty() && !isRinging) {
                    startRinging(peerKey)
                }
            }
            ACTION_STOP_RINGING -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(peerPublicKey: String) {
        isRinging = true

        val callIntent = Intent(this, CallActivity::class.java).apply {
            putExtra("EXTRA_PEER_PUBLIC_KEY", URLEncoder.encode(peerPublicKey, "UTF-8"))
            putExtra("EXTRA_IS_INCOMING", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, NOTIFICATION_ID, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        startForeground(NOTIFICATION_ID, buildCallNotification(fullScreenPi, "Incoming call"))

        serviceScope.launch {
            val displayName = try {
                contactDao.getByPublicKey(peerPublicKey)?.displayName
                    ?: peerPublicKey.take(12) + "..."
            } catch (e: Exception) {
                Timber.w(e, "Failed to look up contact name for call notification")
                peerPublicKey.take(12) + "..."
            }
            if (isRinging) {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, buildCallNotification(fullScreenPi, displayName))
            }
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                playRingtone()
                startVibration()
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                startVibration()
            }
        }

        timeoutJob = serviceScope.launch {
            delay(60_000L)
            Timber.d("Incoming call ringing timeout — stopping")
            stopRinging()
        }
    }

    private fun buildCallNotification(
        fullScreenPi: PendingIntent,
        contentText: String
    ): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle("Incoming call")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun playRingtone() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
            audioFocusRequest = focusRequest
            (getSystemService(AUDIO_SERVICE) as AudioManager).requestAudioFocus(focusRequest)

            var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            if (ringtone == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            }
            ringtone?.apply {
                audioAttributes = attrs
                isLooping = true
                play()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to play ringtone")
        }
    }

    private fun startVibration() {
        try {
            @Suppress("DEPRECATION")
            vibrator = (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to start vibration")
        }
    }

    private fun stopRinging() {
        timeoutJob?.cancel()
        timeoutJob = null
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
        audioFocusRequest?.let {
            try { (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) } catch (_: Exception) {}
        }
        audioFocusRequest = null
        if (isRinging) {
            isRinging = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isRinging = false
        timeoutJob?.cancel()
        try { ringtone?.stop() } catch (_: Exception) {}
        try { vibrator?.cancel() } catch (_: Exception) {}
        audioFocusRequest?.let {
            try { (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) } catch (_: Exception) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming call alerts"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
