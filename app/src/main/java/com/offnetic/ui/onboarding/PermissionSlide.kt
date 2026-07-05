package com.offnetic.ui.onboarding

import android.Manifest
import android.os.Build
import timber.log.Timber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offnetic.ui.theme.Spacing

private val permConfigs = listOf(
    PermConfig(
        tag = "01 / 03",
        title = "Find people\nnearby",
        desc = "Offnetic uses Bluetooth and Wi-Fi to detect trusted contacts around you. Your location is never stored or shared \u2014 it never leaves your device.",
        perms = listOf("Bluetooth", "Nearby Wi-Fi", "Location"),
        btnLabel = "Allow Connectivity",
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    ),
    PermConfig(
        tag = "02 / 03",
        title = "Scan & speak\nfreely",
        desc = "Camera is used to scan QR codes when adding trusted contacts. Microphone powers end-to-end encrypted voice messages and calls.",
        perms = listOf("Camera", "Microphone"),
        btnLabel = "Allow Camera & Mic",
        permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    ),
    PermConfig(
        tag = "03 / 03",
        title = "Stay in\nthe loop",
        desc = "Get pinged when a trusted contact is physically nearby. No background tracking — alerts fire only when someone you know is close.",
        perms = listOf("Notifications"),
        btnLabel = "Allow Notifications",
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf()
        }
    )
)

private data class PermConfig(
    val tag: String,
    val title: String,
    val desc: String,
    val perms: List<String>,
    val btnLabel: String,
    val permissions: Array<String>
)

@Composable
fun PermissionSlide(
    title: String = "",
    onNext: () -> Unit = {}
) {
    val index = when {
        title.contains("Connectivity") -> 0
        title.contains("Camera") || title.contains("Microphone") -> 1
        else -> 2
    }
    val config = permConfigs[index]
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Timber.w("Permissions denied: %s", denied.joinToString())
        }
        // Always proceed — runtime permissions are re-checked at feature use-sites
        onNext()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Color(0xFF0A0A0A))
    ) {
        NoiseOverlay()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(Spacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                (0..2).forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (i == index) 20.dp else 6.dp,
                                height = 6.dp
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == index) Color.White else Color(0x2DFFFFFF))
                    )
                    if (i < 2) Spacer(modifier = Modifier.width(Spacing.sm))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xxl),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = config.tag,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.5.sp,
                    color = Color(0x4DFFFFFF)
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                val iconColor = Color(0xE6FFFFFF)
                Canvas(modifier = Modifier.size(44.dp)) {
                    when (index) {
                        0 -> {
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.65f)
                                lineTo(size.width * 0.3f, size.height * 0.4f)
                                arcTo(
                                    androidx.compose.ui.geometry.Rect(
                                        size.width * 0.3f, size.height * 0.3f,
                                        size.width * 0.8f, size.height * 0.8f
                                    ), 180f, 140f, false
                                )
                            }
                            drawPath(p, iconColor, style = Stroke(width = 1.5f))
                            drawCircle(iconColor, size.width * 0.08f, center = Offset(size.width * 0.5f, size.height * 0.45f), style = Stroke(width = 1.5f))
                            drawCircle(iconColor, size.width * 0.18f, center = Offset(size.width * 0.5f, size.height * 0.45f), style = Stroke(width = 1.5f))
                            drawCircle(iconColor, size.width * 0.30f, center = Offset(size.width * 0.5f, size.height * 0.45f), style = Stroke(width = 1.5f))
                        }
                        1 -> {
                            drawRoundRect(iconColor, topLeft = Offset(size.width * 0.15f, size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f), style = Stroke(width = 1.5f))
                            drawCircle(iconColor, size.width * 0.1f, center = Offset(size.width * 0.5f, size.height * 0.65f), style = Stroke(width = 1.5f))
                            drawCircle(iconColor, size.width * 0.2f, center = Offset(size.width * 0.5f, size.height * 0.65f), style = Stroke(width = 1.5f))
                            drawRect(iconColor, topLeft = Offset(size.width * 0.42f, size.height * 0.78f), size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.12f))
                        }
                        2 -> {
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.3f)
                                lineTo(size.width * 0.3f, size.height * 0.2f)
                                arcTo(
                                    androidx.compose.ui.geometry.Rect(
                                        size.width * 0.2f, size.height * 0.1f,
                                        size.width * 0.6f, size.height * 0.5f
                                    ), 180f, 180f, false
                                )
                                lineTo(size.width * 0.7f, size.height * 0.6f)
                                lineTo(size.width * 0.8f, size.height * 0.7f)
                                lineTo(size.width * 0.8f, size.height * 0.8f)
                            }
                            drawPath(p, iconColor, style = Stroke(width = 1.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                Text(
                    text = config.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = config.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0x73FFFFFF)
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    config.perms.forEach { p ->
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Text(
                                text = p,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0x80FFFFFF),
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl)
            ) {
                Button(
                    onClick = {
                        if (config.permissions.isNotEmpty()) {
                            launcher.launch(config.permissions)
                        } else {
                            onNext()
                        }
                    },
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
                        text = config.btnLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "You can change this anytime in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x33FFFFFF),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
