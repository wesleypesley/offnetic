package com.offnetic.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offnetic.ui.theme.FontFamilySyne
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onDone: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    var ringScale by remember { mutableStateOf(0.6f) }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
        delay(100)
        ringScale = 1f
        delay(2600)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        NoiseOverlay()

        Canvas(
            modifier = Modifier
                .size(320.dp)
                .scale(ringScale)
        ) {
            drawCircle(
                color = Color(0x0FFFFFFF),
                radius = size.minDimension / 2,
                style = Stroke(width = 1f)
            )
        }

        Canvas(
            modifier = Modifier
                .size(220.dp)
                .scale(ringScale)
        ) {
            drawCircle(
                color = Color(0x14FFFFFF),
                radius = size.minDimension / 2,
                style = Stroke(width = 1f)
            )
        }

        Canvas(
            modifier = Modifier.size(160.dp)
        ) {
            drawCircle(
                color = Color(0x0FFFFFFF),
                radius = size.minDimension / 2
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(if (visible) 1f else 0.9f)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(36.dp)) {
                    val cx = size.width / 2
                    drawCircle(Color(0xFF0A0A0A), radius = cx * 0.25f, center = Offset(cx, cx))
                    drawCircle(Color(0xFF0A0A0A), radius = cx * 0.5625f, center = Offset(cx, cx), style = Stroke(width = 2f))
                    drawCircle(
                        Color(0xFF0A0A0A),
                        radius = cx * 0.875f,
                        center = Offset(cx, cx),
                        style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Offnetic",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "OFF-GRID. ENCRYPTED. LOCAL.",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                color = Color(0x59FFFFFF)
            )
        }
    }
}

@Composable
fun NoiseOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val random = kotlin.random.Random(42)
        for (i in 0 until 200) {
            drawCircle(
                Color.White,
                radius = 1.5f,
                center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                alpha = 0.025f
            )
        }
    }
}
