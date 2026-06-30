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
import com.offnetic.data.crypto.NostrIdentityManager
import com.offnetic.data.crypto.nostr.Hex
import com.offnetic.data.local.db.dao.RelayStateDao
import com.offnetic.data.relay.RelayFilter
import com.offnetic.data.relay.RelayPool
import com.offnetic.data.relay.AttachmentRelayResender
import com.offnetic.data.relay.RelayOutboxProcessor
import com.offnetic.data.relay.RelayInboxHandler
import com.offnetic.data.relay.RelayRequestManager
import com.offnetic.data.crypto.nostr.GiftWrap
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
    @Inject lateinit var nostrIdentityManager: NostrIdentityManager
    @Inject lateinit var relayPool: RelayPool
    @Inject lateinit var relayOutboxProcessor: RelayOutboxProcessor
    @Inject lateinit var attachmentRelayResender: AttachmentRelayResender
    @Inject lateinit var relayInboxHandler: RelayInboxHandler
    @Inject lateinit var relayRequestManager: RelayRequestManager
    @Inject lateinit var relayStateDao: RelayStateDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isNcapActive = false
    private var isRelayActive = false
    private var bluetoothPaused = false
    private var restartJob: kotlinx.coroutines.Job? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var shutdownIntent: PendingIntent

    companion object {
        const val CHANNEL_ID = "offnetic_ncap_channel"
        const val NOTIFICATION_ID = 1001

        fun computeSince(lastSeenSec: Long?): Long {
            val nowSec = System.currentTimeMillis() / 1000
            val floor = nowSec - 3L * 24 * 60 * 60
            val backdateMargin = 2L * 24 * 60 * 60
            val candidate = (lastSeenSec ?: floor) - backdateMargin
            return maxOf(candidate, floor)
        }
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
        shutdownIntent = PendingIntent.getService(
            this, 1,
            Intent(this, NcapForegroundService::class.java).apply { action = "com.offnetic.SHUTDOWN" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.offnetic.SHUTDOWN") {
            ncapManager.stopAll()
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
            return START_NOT_STICKY
        }
        updateNotification("Listening for contacts")
        startForeground(NOTIFICATION_ID, buildNotification("Listening for contacts"))

        if (!isNcapActive) {
            startNcap()
        }
        if (!isRelayActive) {
            startRelay()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
        ncapManager.stopAll()
        isNcapActive = false
        isRelayActive = false
        serviceScope.cancel()
        relayPool.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRelay() {
        isRelayActive = true
        serviceScope.launch(Dispatchers.IO) {
            try {
                relayPool.connect()
                delay(5000)

                val keyPair = nostrIdentityManager.getKeyPair() ?: return@launch
                val myNpubHex = Hex.encode(keyPair.publicKey)
                val lastSeen = relayStateDao.getLastSeen()
                val since = computeSince(lastSeen)

                relayPool.subscribe(
                    "offnetic-inbox",
                    RelayFilter(kinds = listOf(GiftWrap.KIND_GIFT_WRAP), pTags = listOf(myNpubHex), since = since)
                )
                Timber.d("RelaySvc subscribed since=$since pTag=${myNpubHex.take(8)}")

                launch {
                    relayPool.events.collect { event ->
                        try {
                            relayInboxHandler.handleGiftWrap(event)
                            relayStateDao.setLastSeen(event.createdAt)
                        } catch (e: Exception) {
                            Timber.e(e, "Inbound handler failed")
                        }
                    }
                }
                launch {
                    relayPool.acks.collect {
                        try { relayOutboxProcessor.handleAck(it) } catch (e: Exception) { Timber.e(e, "ACK handler failed") }
                    }
                }
                launch {
                    while (true) {
                        delay(60_000L)
                        try {
                            val refreshSince = computeSince(relayStateDao.getLastSeen())
                            relayPool.subscribe("offnetic-inbox", RelayFilter(kinds = listOf(GiftWrap.KIND_GIFT_WRAP), pTags = listOf(myNpubHex), since = refreshSince))
                            Timber.d("RelaySvc subscription refreshed since=$refreshSince")
                        } catch (e: Exception) { Timber.e(e, "Subscription refresh failed") }
                    }
                }
                launch {
                    while (true) {
                        try { attachmentRelayResender.processPending() } catch (e: Exception) { Timber.e(e, "Attachment resend drain failed") }
                        delay(30_000L)
                    }
                }
                while (true) {
                    try { relayOutboxProcessor.processPending() } catch (e: Exception) { Timber.e(e, "Outbox drain failed") }
                    try { relayRequestManager.republishOutbound() } catch (e: Exception) { Timber.e(e, "Outbound request republish failed") }
                    delay(30_000L)
                }
            } catch (e: Exception) {
                isRelayActive = false
                Timber.e(e, "Relay start failed")
            }
        }
    }

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
            .addAction(0, "Shutdown", shutdownIntent)
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
