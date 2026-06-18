package com.offnetic.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.offnetic.MainActivity
import com.offnetic.R
import com.offnetic.data.local.db.dao.ContactDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class MessageNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeChatTracker: ActiveChatTracker,
    private val contactDao: ContactDao
) {
    companion object {
        private const val CHANNEL_ID = "offnetic_messages"
        private const val CHANNEL_NAME = "Messages"
        private const val NOTIFICATION_ID_BASE = 5000
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val pendingChatNavigation = MutableStateFlow<String?>(null)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming message notifications"
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun notifyIfNeeded(senderPublicKey: String) {
        if (activeChatTracker.activeChatKey == senderPublicKey) return

        val displayName = try {
            contactDao.getByPublicKey(senderPublicKey)?.displayName
                ?: senderPublicKey.take(12) + "..."
        } catch (e: Exception) {
            Timber.w(e, "Failed to look up contact name for notification")
            senderPublicKey.take(12) + "..."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_NAVIGATE_CHAT_KEY", senderPublicKey)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val notificationId = NOTIFICATION_ID_BASE + abs(senderPublicKey.hashCode() % 1000)

        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(displayName)
            .setContentText("New message")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(notificationId, notification)
    }

    fun dismissForContact(senderPublicKey: String) {
        val notificationId = NOTIFICATION_ID_BASE + abs(senderPublicKey.hashCode() % 1000)
        manager.cancel(notificationId)
    }
}
