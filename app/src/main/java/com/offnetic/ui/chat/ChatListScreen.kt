package com.offnetic.ui.chat

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.offnetic.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.OffneticColors
import com.offnetic.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit = {},
    onScanQr: () -> Unit = {},
    onRequests: () -> Unit = {},
    onShutdown: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ChatListContent(
                onChatClick = onChatClick,
                onScanQr = onScanQr,
                onRequests = onRequests,
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
    onRequests: () -> Unit = {},
    onShutdown: () -> Unit = {},
    viewModel: ChatListViewModel
) {
    val chatSummaries by viewModel.chatSummaries.collectAsState()
    val profileDisplayName by viewModel.profileDisplayName.collectAsState()
    val pendingRequestCount by viewModel.pendingRequestCount.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
            Header(
                onScanQr = onScanQr,
                onRequests = onRequests,
                onShutdown = onShutdown,
                onShowProfile = { showProfileDialog = true },
                isDiscovering = isDiscovering,
                pendingRequestCount = pendingRequestCount
            )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
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
            displayName = profileDisplayName.ifEmpty { stringResource(R.string.profile_no_username) },
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
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.profile_username_label),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = OffneticColors.textHint
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelLarge,
                    color = OffneticColors.accentLink
                )
            }
        }
    )
}

@Composable
private fun Header(
    onScanQr: () -> Unit,
    onRequests: () -> Unit = {},
    onShutdown: () -> Unit = {},
    onShowProfile: () -> Unit = {},
    isDiscovering: Boolean,
    pendingRequestCount: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl, bottom = Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Offnetic",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.xxs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF4ADE80), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(if (isDiscovering) R.string.chat_list_discovering else R.string.chat_list_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = OffneticColors.textDisabled
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                QrButton(onClick = onScanQr)
                RequestsButton(onClick = onRequests, badgeCount = pendingRequestCount)
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
            Icon(
                painter = painterResource(R.drawable.ic_qr_code_scanner),
                contentDescription = stringResource(R.string.cd_scan_qr),
                tint = Color(0xB3FFFFFF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RequestsButton(onClick: () -> Unit, badgeCount: Int) {
    Box(modifier = Modifier.size(38.dp)) {
        Surface(
            modifier = Modifier
                .size(38.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            color = Color(0x12FFFFFF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = stringResource(R.string.cd_requests),
                    tint = Color(0xB3FFFFFF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color.White
                )
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
            Icon(
                painter = painterResource(R.drawable.ic_power_settings_new),
                contentDescription = stringResource(R.string.cd_shutdown),
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(20.dp)
            )
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
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.cd_settings),
                tint = Color(0xB3FFFFFF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.chat_list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = OffneticColors.textHint
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
            .padding(vertical = Spacing.md, horizontal = 0.dp),
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0x99FFFFFF)
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.md))

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
                    val statusColor = when (summary.reachability) {
                        com.offnetic.domain.model.ChatReachability.LOCAL -> Color(0xFF4ADE80)
                        com.offnetic.domain.model.ChatReachability.INTERNET_RELAY -> Color(0xFF60A5FA)
                        com.offnetic.domain.model.ChatReachability.OFFLINE -> null
                    }
                    if (statusColor != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                    }
                    Text(
                        text = summary.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatTimestamp(summary.lastTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x40FFFFFF)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = summary.lastMessage,
                style = MaterialTheme.typography.bodySmall,
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