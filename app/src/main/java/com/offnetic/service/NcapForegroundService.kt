package com.offnetic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.offnetic.MainActivity
import com.offnetic.R
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.datastore.PreferencesRepository
import com.offnetic.data.nearby.NcapManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NcapForegroundService : Service() {

    @Inject lateinit var ncapManager: NcapManager
    @Inject lateinit var identityDao: IdentityDao
    @Inject lateinit var signalProtocolManager: SignalProtocolManager
    @Inject lateinit var prefs: PreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isNcapActive = false

    companion object {
        const val CHANNEL_ID = "offnetic_ncap_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Scout Mode active")
            .setContentText("Listening for calls, messages, and nearby trusted contacts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (!isNcapActive) {
            startNcap()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        ncapManager.stopAll()
        isNcapActive = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNcap() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                signalProtocolManager.initialize()
                signalProtocolManager.ensurePreKeys()
                val identity = identityDao.getIdentity()

                val isDiscoverable = prefs.isDiscoverable.firstOrNull() ?: true
                val isBgScanning = prefs.isBackgroundScanningEnabled.firstOrNull() ?: true

                if (identity != null && isDiscoverable) {
                    ncapManager.startAdvertising(identity.publicKey)
                }
                if (isBgScanning) {
                    ncapManager.startDiscovery()
                }
                isNcapActive = true
                Timber.d("NCAPI started — advertise=$isDiscoverable, discover=$isBgScanning")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start NCAPI: ${e.message}")
                android.util.Log.e("NcapService", "NCAPI start failed", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nearby Connections",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Offnetic listening for nearby devices"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
