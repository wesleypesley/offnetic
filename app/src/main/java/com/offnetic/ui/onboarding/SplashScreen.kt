package com.offnetic.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.offnetic.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onDone: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        delay(2800)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.offnetic_logo),
            contentDescription = "Offnetic",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun NoiseOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val random = kotlin.random.Random(42)
        val count = (size.width * size.height / 4000).toInt().coerceIn(150, 400)
        for (i in 0 until count) {
            drawCircle(
                Color.White,
                radius = random.nextFloat() * 1.2f + 0.3f,
                center = Offset(
                    random.nextFloat() * size.width,
                    random.nextFloat() * size.height
                ),
                alpha = random.nextFloat() * 0.02f + 0.01f
            )
        }
    }
}
