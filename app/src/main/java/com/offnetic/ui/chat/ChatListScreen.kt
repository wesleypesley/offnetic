package com.offnetic.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.FontFamilySyne
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit = {},
    onScanQr: () -> Unit = {},
    onNearbyClick: () -> Unit = {},
    onShutdown: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ChatListContent(
                onChatClick = onChatClick,
                onScanQr = onScanQr,
                onShutdown = onShutdown,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun ChatListContent(
    onChatClick: (String) -> Unit = {},
    onScanQr: () -> Unit = {},
    onShutdown: () -> Unit = {},
    viewModel: ChatListViewModel
) {
    val chatSummaries by viewModel.chatSummaries.collectAsState()
    val profileDisplayName by viewModel.profileDisplayName.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Header(
            onScanQr = onScanQr,
            onShutdown = onShutdown,
            onShowProfile = { showProfileDialog = true },
            isDiscovering = true,
            profileDisplayName = profileDisplayName
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            if (chatSummaries.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(chatSummaries, key = { it.contactPublicKey }) { summary ->
                    ChatListItem(
                        summary = summary,
                        onClick = { onChatClick(summary.contactPublicKey) }
                    )
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileDialog(
            displayName = profileDisplayName.ifEmpty { "No username set" },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
private fun ProfileDialog(
    displayName: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Profile",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = "Username",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = Color(0x40FFFFFF)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayName,
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    )
}

@Composable
private fun Header(
    onScanQr: () -> Unit,
    onShutdown: () -> Unit = {},
    onShowProfile: () -> Unit = {},
    isDiscovering: Boolean,
    profileDisplayName: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Offnetic",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF4ADE80), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isDiscovering) "Discovering nearby" else "Offline",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color(0x4DFFFFFF)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QrButton(onClick = onScanQr)
                ShutdownButton(onClick = onShutdown)
                GearButton(onClick = onShowProfile)
            }
        }
    }
}

@Composable
private fun QrButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0x12FFFFFF)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val s = Stroke(width = 1.5f)
                val strokeColor = Color(0xB3FFFFFF)
                val cw = size.width
                val cell = cw / 7f
                val cornerR = cell * 0.35f

                fun drawCell(col: Int, row: Int, filled: Boolean) {
                    if (filled) {
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = Offset(col * cell * 2f, row * cell * 2f),
                            size = androidx.compose.ui.geometry.Size(cell, cell),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR),
                            style = s
                        )
                    }
                }

                drawRoundRect(color = strokeColor, topLeft = Offset(cell, cell), size = androidx.compose.ui.geometry.Size(cell * 5f, cell * 5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR), style = s)
                drawRoundRect(color = strokeColor, topLeft = Offset(cell * 2f, cell * 2f), size = androidx.compose.ui.geometry.Size(cell * 3f, cell * 3f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerR, cornerR), style = s)

                drawCell(1, 1, true); drawCell(2, 1, true); drawCell(1, 2, true)
                drawCell(1, 1, true); drawCell(2, 2, true)
            }
        }
    }
}

@Composable
private fun AvatarInitial(initial: String) {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1AFFFFFF)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xB3FFFFFF)
            )
        }
    }
}

@Composable
private fun ShutdownButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1AEF4444)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeWidth = 2.5f
                val stroke = Stroke(width = strokeWidth)
                val color = Color(0xFFEF4444)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val gap = size.height * 0.08f
                val top = size.height * 0.12f
                val bottom = cy + gap

                drawLine(
                    color = color,
                    start = Offset(cx, top),
                    end = Offset(cx, bottom),
                    strokeWidth = strokeWidth
                )
                val arcRadius = size.width * 0.32f
                drawArc(
                    color = color,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius, cy - arcRadius + gap),
                    size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                    style = stroke
                )
            }
        }
    }
}

@Composable
private fun GearButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0x12FFFFFF)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(17.dp)) {
                val color = Color(0xB3FFFFFF)
                val strokeWidth = 1.8f
                val stroke = Stroke(width = strokeWidth)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.width * 0.44f
                val innerR = size.width * 0.2f
                val teethLen = size.width * 0.08f
                val teethCount = 8
                for (i in 0 until teethCount) {
                    val angle = Math.toRadians((i * 360.0 / teethCount).toDouble())
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    val x1 = cx + cos * (outerR - teethLen)
                    val y1 = cy + sin * (outerR - teethLen)
                    val x2 = cx + cos * (outerR + teethLen)
                    val y2 = cy + sin * (outerR + teethLen)
                    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth)
                }
                drawCircle(color = color, radius = outerR, center = Offset(cx, cy), style = stroke)
                drawCircle(color = color, radius = innerR, center = Offset(cx, cy), style = stroke)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No conversations yet",
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = Color(0x40FFFFFF)
        )
    }
}

@Composable
private fun ChatListItem(
    summary: ChatSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0x14FFFFFF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = summary.displayName.take(2).uppercase(),
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0x99FFFFFF)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (summary.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF4ADE80), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = summary.displayName,
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTimestamp(summary.lastTimestamp),
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = Color(0x40FFFFFF)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary.lastMessage,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0x4DFFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}