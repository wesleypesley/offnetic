package com.offnetic.ui.contacts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.offnetic.ui.theme.FontFamilySyne
import com.offnetic.ui.theme.Spacing

@Composable
fun MyQrScreen(
    onBack: () -> Unit = {},
    viewModel: MyQrViewModel = hiltViewModel()
) {
    val qrPayload by viewModel.qrPayload.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

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
                Text("✕", fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
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
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color(0xFF0A0A0A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Your QR Code",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Have trusted contacts scan this to establish an encrypted session.",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color(0x73FFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xxxl)
            )

            Spacer(modifier = Modifier.height(Spacing.xl))
            Text(
                text = "ID: ${displayName}...",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0x40FFFFFF),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.3f))
            Spacer(modifier = Modifier.height(Spacing.xxxl))
        }
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
        } catch (_: WriterException) { }
    }
}
