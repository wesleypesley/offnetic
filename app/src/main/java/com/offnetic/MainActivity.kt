package com.offnetic

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.offnetic.ui.MainViewModel
import com.offnetic.ui.contacts.DeepLink
import com.offnetic.ui.navigation.OffneticNavHost
import com.offnetic.ui.theme.Theme
import com.offnetic.util.MessageNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var messageNotificationManager: MessageNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            org.signal.libsignal.protocol.SignalProtocolAddress("smoke_test", 1)
            android.util.Log.d("OffneticSecurity", "libsignal classes resolved on this device")
        } catch (e: Throwable) {
            android.util.Log.e("OffneticSecurity", "\u274C RUNTIME CRASH: Failed to resolve desugared libsignal classes!", e)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        handleChatNavigationIntent(intent)
        handleAddContactIntent(intent)

        setContent {
            Theme {
                OffneticNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleChatNavigationIntent(intent)
        handleAddContactIntent(intent)
    }

    private fun handleChatNavigationIntent(intent: Intent?) {
        intent?.getStringExtra("EXTRA_NAVIGATE_CHAT_KEY")?.let { key ->
            intent.removeExtra("EXTRA_NAVIGATE_CHAT_KEY")
            messageNotificationManager.pendingChatNavigation.value = key
        }
    }

    private fun handleAddContactIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val payload = intent.dataString?.let { DeepLink.parseAddLink(it) } ?: return
        intent.data = null
        messageNotificationManager.pendingPairingPayload.value = payload
    }
}
