package com.offnetic.ui.contacts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactDetailScreen(
    onBack: () -> Unit = {},
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHardDeleteDialog by remember { mutableStateOf(false) }
    var showSoftDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionMessage) {
        if (uiState.actionMessage != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        text = "← Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0x73FFFFFF)
                    )
                }
                return@Column
            }

            val contact = uiState.contact
            if (contact == null && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Contact not found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0x73FFFFFF)
                    )
                }
                return@Column
            }

            if (contact == null) return@Column

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
            Text(
                text = (contact.displayName).take(2).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            DetailRow("Public Key", contact.publicKey.take(24) + "...")
            DetailRow("Added", formatTimestamp(contact.addedAt))
            DetailRow("Last Seen", formatTimestamp(contact.lastSeenAt))
            DetailRow("Verified", if (contact.isVerified) "Yes" else "No")

            Spacer(modifier = Modifier.height(Spacing.xxl))

            uiState.actionMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4ADE80),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.lg)
                        .background(Color(0xFF0A0A0A))
                )
            }

            OutlinedButton(
                onClick = { showSoftDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0x73FFFFFF)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0x14FFFFFF))
                )
            ) {
                Text("Remove Contact", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedButton(
                onClick = { showHardDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0x24FFFFFF))
                )
            ) {
                Text("Delete Contact & Data", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(Spacing.xxxl))
        }
    }

    if (showSoftDeleteDialog) {
        ConfirmDialog(
            title = "Remove Contact",
            message = "This contact will be hidden from your list. The encrypted session and chat history are preserved. You can restore this by re-scanning their QR code.",
            confirmText = "Remove",
            confirmColor = Color.White,
            onConfirm = {
                showSoftDeleteDialog = false
                viewModel.softDelete()
                onBack()
            },
            onDismiss = { showSoftDeleteDialog = false }
        )
    }

    if (showHardDeleteDialog) {
        ConfirmDialog(
            title = "Delete All Data",
            message = "This will permanently delete all messages, media, and encryption keys for this contact. This cannot be undone. The peer will be notified of session termination.",
            confirmText = "Delete Everything",
            confirmColor = Color(0xFFEF4444),
            onConfirm = {
                showHardDeleteDialog = false
                viewModel.hardDelete()
                onBack()
            },
            onDismiss = { showHardDeleteDialog = false }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = Color(0x40FFFFFF)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
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
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0x73FFFFFF)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.labelLarge,
                    color = confirmColor
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val sdf = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
