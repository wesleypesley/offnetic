package com.offnetic.ui.contacts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.offnetic.ui.theme.Spacing
import java.io.File

@Composable
fun MyQrScreen(
    onBack: () -> Unit = {},
    viewModel: MyQrViewModel = hiltViewModel()
) {
    val qrPayload by viewModel.qrPayload.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = Spacing.xxxl, start = Spacing.sm)
            ) {
                Text("‹", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.weight(0.25f))

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(Spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                if (qrPayload != null) {
                    QrCodeCanvas(payload = qrPayload!!, modifier = Modifier.size(208.dp))
                } else {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0A0A0A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Your QR Code",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Have trusted contacts scan this to establish an encrypted session.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0x73FFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xxxl)
            )

            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = "ID: ${displayName}...",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x40FFFFFF),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Button(
                onClick = { qrPayload?.let { shareQrImage(context, it) } },
                enabled = qrPayload != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxl)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A0A0A)
                )
            ) {
                Text("Share QR image", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Button(
                onClick = {
                    qrPayload?.let {
                        clipboard.setText(AnnotatedString(DeepLink.buildAddLink(it)))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = qrPayload != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxl)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0x40FFFFFF)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                Text("Copy link", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }

            Spacer(modifier = Modifier.weight(0.3f))
            Spacer(modifier = Modifier.height(Spacing.xxxl))
        }
    }
}

private fun shareQrImage(context: Context, payload: String) {
    val bitmap = qrBitmap(payload) ?: return
    try {
        val file = File(context.cacheDir, "offnetic_qr.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR code"))
    } catch (_: Exception) {
    }
}

private fun qrBitmap(payload: String, size: Int = 512): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(
                    x, y,
                    if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bmp
    } catch (_: WriterException) {
        null
    }
}

@Composable
private fun QrCodeCanvas(payload: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, 400, 400)
            val cellW = size.width / bitMatrix.width
            val cellH = size.height / bitMatrix.height

            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    if (bitMatrix.get(x, y)) {
                        drawRect(
                            Color(0xFF0A0A0A),
                            topLeft = Offset(x * cellW, y * cellH),
                            size = Size(cellW, cellH)
                        )
                    }
                }
            }
        } catch (_: WriterException) {
        }
    }
}
