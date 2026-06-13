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
import com.offnetic.ui.theme.FontFamilySyne
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
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

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading...",
                        fontFamily = FontFamilySyne,
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
                        fontFamily = FontFamilySyne,
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
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = contact.displayName,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            DetailRow("Public Key", contact.publicKey.take(24) + "...")
            DetailRow("Added", formatTimestamp(contact.addedAt))
            DetailRow("Last Seen", formatTimestamp(contact.lastSeenAt))
            DetailRow("Verified", if (contact.isVerified) "Yes" else "No")

            Spacer(modifier = Modifier.height(32.dp))

            uiState.actionMessage?.let { msg ->
                Text(
                    text = msg,
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color(0xFF4ADE80),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
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
                Text("Remove Contact", fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                Text("Delete Contact & Data", fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
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
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = Color(0x40FFFFFF)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
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

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Never"
    val sdf = SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
