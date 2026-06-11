package com.offnetic.ui.contacts

import android.util.DisplayMetrics
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.offnetic.ui.theme.FontFamilySyne
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    onScanComplete: (QrPairingData) -> Unit,
    onBack: () -> Unit = {},
    onShowMyQr: () -> Unit = {},
    viewModel: QrScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val scanLineAnim = rememberInfiniteTransition(label = "scan")
    val scanLineY by scanLineAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    LaunchedEffect(state.scannedData) {
        state.scannedData?.let { onScanComplete(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also { p ->
                                p.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { imageAnalysis ->
                                    imageAnalysis.setAnalyzer(
                                        Executors.newSingleThreadExecutor()
                                    ) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage, imageProxy.imageInfo.rotationDegrees
                                            )
                                            BarcodeScanning.getClient().process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        barcode.rawValue?.let { raw ->
                                                            QrPairingData.fromQrPayload(raw)?.let { data ->
                                                                viewModel.onQrScanned(data)
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            val selector = CameraSelector.DEFAULT_BACK_CAMERA
                            provider.unbindAll()
                            val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                            camera = boundCamera
                        } catch (e: Exception) {
                            cameraError = e.message
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        ViewfinderOverlay(scanLineProgress = scanLineY)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            Text(
                text = "Scan QR Code",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Point your camera at the QR code to start a secure, encrypted handshake.",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color(0x73FFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )

            Spacer(modifier = Modifier.weight(0.7f))

            Button(
                onClick = onShowMyQr,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A0A0A)
                )
            ) {
                Text(
                    text = "Show My QR Code",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "You can change this anytime in Settings",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0x33FFFFFF)
            )
            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
        }

        androidx.compose.material3.FloatingActionButton(
            onClick = {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 160.dp),
            containerColor = if (isFlashOn) Color.White else Color(0x1AFFFFFF),
            contentColor = if (isFlashOn) Color(0xFF0A0A0A) else Color.White
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                drawLine(Color.White, Offset(cx, 4f), Offset(cx, cy - 6f), strokeWidth = 2.5f)
                drawLine(Color.White, Offset(cx, cy + 6f), Offset(cx, size.height - 4f), strokeWidth = 2.5f)
                drawLine(Color.White, Offset(4f, size.height - 4f), Offset(cx - 6f, size.height - 4f), strokeWidth = 2.5f)
                drawLine(Color.White, Offset(cx + 6f, size.height - 4f), Offset(size.width - 4f, size.height - 4f), strokeWidth = 2.5f)
                drawLine(Color.White, Offset(size.width - 4f, cy + 6f), Offset(size.width - 4f, size.height - 4f), strokeWidth = 2.5f)
                drawLine(Color.White, Offset(size.width - 4f, 4f), Offset(size.width - 4f, cy - 6f), strokeWidth = 2.5f)
            }
        }

        cameraError?.let { error ->
            Text(
                text = "Camera error: $error",
                fontFamily = FontFamilySyne,
                color = Color(0xFFEF4444),
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        androidx.compose.material3.IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 8.dp)
        ) {
            Text("✕", fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
        }
    }
}

@Composable
private fun ViewfinderOverlay(scanLineProgress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val vfW = size.width * 0.7f
        val vfH = vfW
        val vfX = (size.width - vfW) / 2
        val vfY = (size.height - vfH) / 2
        val vfRect = androidx.compose.ui.geometry.Rect(vfX, vfY, vfX + vfW, vfY + vfH)
        val cornerLen = 40f
        val cornerStroke = 4f
        val white = Color.White

        drawRect(Color(0x80000000), topLeft = Offset(0f, 0f), size = Size(size.width, vfY))
        drawRect(Color(0x80000000), topLeft = Offset(0f, vfY + vfH), size = Size(size.width, size.height - vfY - vfH))
        drawRect(Color(0x80000000), topLeft = Offset(0f, vfY), size = Size(vfX, vfH))
        drawRect(Color(0x80000000), topLeft = Offset(vfX + vfW, vfY), size = Size(size.width - vfX - vfW, vfH))

        drawLine(white, Offset(vfX, vfY + cornerLen), Offset(vfX, vfY), strokeWidth = cornerStroke)
        drawLine(white, Offset(vfX, vfY), Offset(vfX + cornerLen, vfY), strokeWidth = cornerStroke)

        drawLine(white, Offset(vfX + vfW - cornerLen, vfY), Offset(vfX + vfW, vfY), strokeWidth = cornerStroke)
        drawLine(white, Offset(vfX + vfW, vfY), Offset(vfX + vfW, vfY + cornerLen), strokeWidth = cornerStroke)

        drawLine(white, Offset(vfX, vfY + vfH - cornerLen), Offset(vfX, vfY + vfH), strokeWidth = cornerStroke)
        drawLine(white, Offset(vfX, vfY + vfH), Offset(vfX + cornerLen, vfY + vfH), strokeWidth = cornerStroke)

        drawLine(white, Offset(vfX + vfW - cornerLen, vfY + vfH), Offset(vfX + vfW, vfY + vfH), strokeWidth = cornerStroke)
        drawLine(white, Offset(vfX + vfW, vfY + vfH - cornerLen), Offset(vfX + vfW, vfY + vfH), strokeWidth = cornerStroke)

        val scanY = vfY + (vfH * scanLineProgress)
        drawLine(
            color = Color(0xCCFFFFFF),
            start = Offset(vfX + 16f, scanY),
            end = Offset(vfX + vfW - 16f, scanY),
            strokeWidth = 2f
        )
    }
}
