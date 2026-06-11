package com.offnetic.ui.nearby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.domain.model.ConnectionState
import com.offnetic.domain.model.NearbyState
import com.offnetic.ui.theme.FontFamilySyne

@Composable
fun NearbyPeersScreen(
    onScanQr: () -> Unit = {},
    onChatsClick: () -> Unit = {},
    viewModel: NearbyViewModel = hiltViewModel()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A),
        floatingActionButton = {
            FloatingActionButton(onClick = onScanQr) {
                Text(text = "QR", fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NearbyPeersContent(onScanQr = onScanQr, viewModel = viewModel)
        }
    }
}

@Composable
fun NearbyPeersContent(
    onScanQr: () -> Unit = {},
    viewModel: NearbyViewModel = hiltViewModel()
) {
    val peers by viewModel.peers.collectAsState()
    val nearbyState by viewModel.nearbyState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NoiseOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nearby",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = nearbyState) {
                is NearbyState.Idle -> StatusLine("Offline", Color(0x40FFFFFF))
                is NearbyState.Advertising -> StatusLine("Advertising...", Color(0x73FFFFFF))
                is NearbyState.Discovering -> StatusLine("Scanning...", Color(0xFF4ADE80))
                is NearbyState.Active -> StatusLine("Active", Color(0xFF4ADE80))
                is NearbyState.Error -> StatusLine("Error: ${state.message}", Color(0xFFEF4444))
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White
            )

            Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))

            if (peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Searching for nearby devices...",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = Color(0x40FFFFFF)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(peers, key = { it.endpointId }) { peer ->
                        PeerCard(
                            peer = peer,
                            onConnect = { viewModel.connectToPeer(peer.endpointId) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun NoiseOverlay() {
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

@Composable
private fun StatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
private fun PeerCard(
    peer: com.offnetic.domain.model.PeerInfo,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peer.displayName.first().uppercase(),
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color(0x99FFFFFF)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = peer.displayName,
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (peer.isContact) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "\u2713",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
            Text(
                text = "ID: ${peer.publicKey.take(16)}...",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0x40FFFFFF)
            )
        }

        Text(
            text = when (peer.connectionState) {
                ConnectionState.DISCONNECTED -> ""
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.FAILED -> "Failed"
            },
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color(0x73FFFFFF)
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (peer.connectionState == ConnectionState.DISCONNECTED) {
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A0A0A)
                ),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text(
                    "Connect",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
