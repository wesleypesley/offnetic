package com.offnetic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.FontFamilySyne
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun AccountScreen(
    onBack: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEraseDialog by remember { mutableStateOf(false) }
    var showLogOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Account",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(28.dp))

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
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            ProfileSection(viewModel)

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "DANGER ZONE".uppercase(),
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = Color(0xFFEF4444)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showEraseDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0x73FFFFFF)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0x14FFFFFF))
                ),
                enabled = !uiState.isLoading
            ) {
                Text("Erase All Messages & Media", fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showLogOutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0x24FFFFFF))
                ),
                enabled = !uiState.isLoading
            ) {
                Text("Erase & Log Out", fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0x24FFFFFF))
                ),
                enabled = !uiState.isLoading
            ) {
                Text(
                    "Delete Account",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF4444)
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
        }
    }

    if (showEraseDialog) {
        ConfirmDialog(
            title = "Erase All Messages & Media",
            message = "This will delete all messages and media across all conversations. Your identity, contacts, and encryption sessions remain intact. You will remain discoverable.",
            confirmText = "Erase",
            confirmColor = Color(0xFFEF4444),
            onConfirm = {
                showEraseDialog = false
                viewModel.eraseAllContent()
            },
            onDismiss = { showEraseDialog = false }
        )
    }

    if (showLogOutDialog) {
        ConfirmDialog(
            title = "Erase & Log Out",
            message = "Erases all messages and media, stops all connections, and returns to the biometric lock screen. Your identity keypair and contacts are preserved. Everything is restored on next biometric unlock.",
            confirmText = "Erase & Log Out",
            confirmColor = Color(0xFFEF4444),
            onConfirm = {
                showLogOutDialog = false
                viewModel.eraseAllContentAndLogOut()
            },
            onDismiss = { showLogOutDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Account",
            message = "This cannot be undone. Destroys all messages, media, contacts, encryption sessions, and your identity keypair. All existing contacts will need to re-add you via a new QR scan.",
            confirmText = "Delete Everything",
            confirmColor = Color(0xFFEF4444),
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteAccount()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun ProfileSection(viewModel: AccountViewModel) {
    val displayName by viewModel.profileDisplayName.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(displayName) }

    Text(
        text = "PROFILE",
        fontFamily = FontFamilySyne,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = Color(0x40FFFFFF)
    )
    Spacer(modifier = Modifier.height(12.dp))

    if (editing) {
        OutlinedTextField(
            value = newName,
            onValueChange = { if (it.length <= 24) newName = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color(0x40FFFFFF),
                unfocusedBorderColor = Color(0x1AFFFFFF),
                containerColor = Color(0x0DFFFFFF)
            ),
            textStyle = TextStyle(
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                if (newName.trim().length >= 2) {
                    viewModel.updateDisplayName(newName.trim())
                    editing = false
                }
            }) {
                Text("Save", color = Color.White, fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { editing = false }) {
                Text("Cancel", color = Color(0x66FFFFFF), fontFamily = FontFamilySyne)
            }
        }
    } else {
        Text(
            text = displayName.ifEmpty { "No username set" },
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { newName = displayName; editing = true }) {
            Text("Edit username", color = Color(0x66FFFFFF), fontFamily = FontFamilySyne, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
