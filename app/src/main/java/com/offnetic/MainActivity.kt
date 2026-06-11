package com.offnetic

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.offnetic.ui.MainViewModel
import com.offnetic.ui.navigation.OffneticNavHost
import com.offnetic.ui.theme.Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val testAddress = org.signal.libsignal.protocol.SignalProtocolAddress("smoke_test", 1)
            android.util.Log.d("OffneticSecurity", "\uD83D\uDE80 CRITICAL SUCCESS: libsignal classes resolved perfectly on this device!")
        } catch (e: Throwable) {
            android.util.Log.e("OffneticSecurity", "\u274C RUNTIME CRASH: Failed to resolve desugared libsignal classes!", e)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            Theme {
                OffneticNavHost()
            }
        }
    }
}
