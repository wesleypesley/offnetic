package com.offnetic.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun IdentityGenerationScreen(
    onDone: () -> Unit = {},
    viewModel: IdentityGenerationViewModel = hiltViewModel()
) {
    var phase by remember { mutableIntStateOf(0) }
    var progress by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    var ringRotation by remember { mutableStateOf(0f) }
    var ring2Rotation by remember { mutableStateOf(0f) }

    val lines = listOf(
        "Generating ECDH P-256 keypair",
        "Deriving public identity",
        "Initialising PQXDH bundle",
        "Sealing into Keystore",
        "Identity secured"
    )
    val activeLine = (progress / 100f * lines.size).toInt().coerceAtMost(lines.size - 1)

    LaunchedEffect(Unit) {
        viewModel.generateIdentity()
        delay(60)
        visible = true
        while (progress < 100) {
            delay(40)
            progress += 2
            ringRotation += 7.2f
            ring2Rotation -= 5.4f
        }
        delay(400)
        phase = 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Color(0xFF0A0A0A))
    ) {
        NoiseOverlay()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                if (phase == 0) {
                    Canvas(modifier = Modifier.size(140.dp).rotate(ringRotation)) {
                        drawCircle(Color(0x14FFFFFF), radius = size.minDimension / 2, style = Stroke(width = 1f))
                        drawArc(Color(0x80FFFFFF), 0f, 30f, false, style = Stroke(width = 1.5f))
                    }
                    Canvas(modifier = Modifier.size(100.dp).rotate(ring2Rotation)) {
                        drawCircle(Color(0x0FFFFFFF), radius = size.minDimension / 2, style = Stroke(width = 1f))
                        drawArc(Color(0x4DFFFFFF), 0f, 20f, false, style = Stroke(width = 1.5f))
                    }
                } else {
                    Canvas(modifier = Modifier.size(140.dp).rotate(ringRotation)) {
                        drawCircle(Color(0x14FFFFFF), radius = size.minDimension * 0.41f, style = Stroke(width = 1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (phase == 0) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    } else {
                        Text(
                            text = "✓",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxxl))

            Text(
                text = if (phase == 0) "Creating your identity" else "Identity created",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = if (phase == 0)
                    "Your cryptographic identity is being generated entirely on this device."
                else
                    "Your keys are sealed in the Android Keystore.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0x59FFFFFF),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xxxl))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x08FFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                    .padding(Spacing.lg)
            ) {
                Column {
                    lines.forEachIndexed { i, line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = Spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            i < activeLine -> Color.White
                                            i == activeLine -> Color(0xFFAAAAAA)
                                            else -> Color(0x33FFFFFF)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    i <= activeLine -> Color(0x99FFFFFF)
                                    else -> Color(0x33FFFFFF)
                                },
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            if (phase == 1) {
                Spacer(modifier = Modifier.height(Spacing.xxl))
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0A0A0A)
                    )
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
