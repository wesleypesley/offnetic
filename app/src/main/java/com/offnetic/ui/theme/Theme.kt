package com.offnetic.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun Theme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    if (!view.isInEditMode) {
        val activity = (context as? Activity)
        activity?.let { applyInsetsStyle(it, darkTheme) }
    }

    OffneticColors(darkTheme = darkTheme, content = content)
}

private fun applyInsetsStyle(activity: Activity, darkTheme: Boolean) {
    val windowInsetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    windowInsetsController.isAppearanceLightStatusBars = !darkTheme
    windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
    activity.window.statusBarColor = Color.Transparent.toArgb()
    activity.window.navigationBarColor = Color.Transparent.toArgb()
}
