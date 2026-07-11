package com.offnetic.ui.onboarding

import android.Manifest
import android.os.Build
import timber.log.Timber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offnetic.R
import com.offnetic.ui.theme.OffneticColors
import com.offnetic.ui.theme.Spacing

private data class PermConfig(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val chipRes: List<Int>,
    @StringRes val btnLabelRes: Int,
    val permissions: Array<String>
)

private val permConfigs = listOf(
    PermConfig(
        titleRes = R.string.perm_title_nearby,
        descRes = R.string.perm_desc_nearby,
        chipRes = listOf(R.string.perm_chip_bluetooth, R.string.perm_chip_nearby_wifi, R.string.perm_chip_location),
        btnLabelRes = R.string.perm_btn_connectivity,
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
        titleRes = R.string.perm_title_camera_mic,
        descRes = R.string.perm_desc_camera_mic,
        chipRes = listOf(R.string.perm_chip_camera, R.string.perm_chip_microphone),
        btnLabelRes = R.string.perm_btn_camera_mic,
        permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    ),
    PermConfig(
        titleRes = R.string.perm_title_notifications,
        descRes = R.string.perm_desc_notifications,
        chipRes = listOf(R.string.perm_chip_notifications),
        btnLabelRes = R.string.perm_btn_notifications,
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf()
        }
    )
)

@Composable
fun PermissionSlide(
    slideIndex: Int,
    onNext: () -> Unit = {}
) {
    val index = slideIndex.coerceIn(0, permConfigs.lastIndex)
    val config = permConfigs[index]

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
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
                permConfigs.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (i == index) 20.dp else 6.dp,
                                height = 6.dp
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == index) OffneticColors.textPrimary else Color(0x2DFFFFFF))
                    )
                    if (i < permConfigs.lastIndex) Spacer(modifier = Modifier.width(Spacing.sm))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.xxl),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.perm_step_tag, index + 1, permConfigs.size),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.5.sp,
                    color = OffneticColors.textDisabled
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
                    text = stringResource(config.titleRes),
                    style = MaterialTheme.typography.displayLarge,
                    color = OffneticColors.textPrimary
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = stringResource(config.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OffneticColors.textMuted
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    config.chipRes.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        ) {
                            Text(
                                text = stringResource(chip),
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(config.btnLabelRes),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = stringResource(R.string.perm_change_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = OffneticColors.textGhost,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
