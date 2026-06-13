package com.offnetic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import com.offnetic.MainActivity
import com.offnetic.R
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.nearby.NcapManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NcapForegroundService : Service() {

    @Inject lateinit var ncapManager: NcapManager
    @Inject lateinit var identityDao: IdentityDao
    @Inject lateinit var signalProtocolManager: SignalProtocolManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isNcapActive = false
    private var bluetoothPaused = false
    private var restartJob: kotlinx.coroutines.Job? = null
    private lateinit var pendingIntent: PendingIntent

    companion object {
        const val CHANNEL_ID = "offnetic_ncap_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    Timber.d("Bluetooth turned off — pausing NCAPI")
                    bluetoothPaused = true
                    restartJob?.cancel()
                    restartJob = null
                    updateNotification("Bluetooth off \u2014 Offnetic paused")
                }
                BluetoothAdapter.STATE_ON -> {
                    Timber.d("Bluetooth turned on — restarting NCAPI")
                    restartJob?.cancel()
                    restartJob = serviceScope.launch(Dispatchers.IO) {
                        delay(2000L)
                        try {
                            val identity = identityDao.getIdentity()
                            if (identity != null) {
                                ncapManager.forceRestart(identity.publicKey)
                                isNcapActive = true
                                bluetoothPaused = false
                                serviceScope.launch(Dispatchers.Main) {
                                    updateNotification("Listening for contacts")
                                }
                                Timber.d("NCAPI restarted after Bluetooth recovery")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to restart NCAPI after Bluetooth recovery")
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification("Listening for contacts")
        startForeground(NOTIFICATION_ID, buildNotification("Listening for contacts"))

        if (!isNcapActive) {
            startNcap()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
        ncapManager.stopAll()
        isNcapActive = false
        bluetoothPaused = false
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

                if (identity != null) {
                    ncapManager.startAdvertising(identity.publicKey)
                }
                ncapManager.startDiscovery()
                isNcapActive = true
                Timber.d("NCAPI started")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start NCAPI: ${e.message}")
                android.util.Log.e("NcapService", "NCAPI start failed", e)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Offnetic")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
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
