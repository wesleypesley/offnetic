package com.offnetic.ui.contacts

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.domain.model.BlockedPeer
import com.offnetic.ui.theme.FontFamilySyne
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlockedContactsScreen(
    onBack: () -> Unit = {},
    viewModel: BlockedContactsViewModel = hiltViewModel()
) {
    val blockedPeers by viewModel.blockedPeers.collectAsState()
    var selectedPeer by remember { mutableStateOf<String?>(null) }
    var showUnblockDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        text = "← Back",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Blocked Contacts",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (blockedPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No blocked contacts",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = Color(0x73FFFFFF)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blockedPeers) { peer ->
                        BlockedPeerCard(
                            peer = peer,
                            onUnblock = {
                                selectedPeer = peer.blockedPublicKey
                                showUnblockDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
        }
    }

    if (showUnblockDialog && selectedPeer != null) {
        ConfirmDialog(
            title = "Unblock Contact",
            message = "Unblocking does not restore the encrypted session. You will need to re-scan their QR code to communicate again. Continue?",
            confirmText = "Unblock",
            confirmColor = Color.White,
            onConfirm = {
                viewModel.unblock(selectedPeer!!)
                showUnblockDialog = false
                selectedPeer = null
            },
            onDismiss = {
                showUnblockDialog = false
                selectedPeer = null
            }
        )
    }
}

@Composable
private fun BlockedPeerCard(
    peer: BlockedPeer,
    onUnblock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                text = peer.displayNameSnapshot.take(2).uppercase(),
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.displayNameSnapshot,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Blocked ${formatBlockDate(peer.blockedAt)}",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0x40FFFFFF)
            )
        }

        OutlinedButton(
            onClick = onUnblock,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0x73FFFFFF)
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0x14FFFFFF))
            )
        ) {
            Text(
                text = "Unblock",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141414),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color(0x73FFFFFF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    color = confirmColor
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    )
}

private fun formatBlockDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
