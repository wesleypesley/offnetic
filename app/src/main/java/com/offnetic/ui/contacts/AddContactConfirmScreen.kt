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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.Spacing

@Composable
fun AddContactConfirmScreen(
    onConfirmed: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: AddContactConfirmViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onConfirmed(state.publicKey)
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

            if (!state.isValid) {
                Spacer(modifier = Modifier.height(Spacing.xxxl))
                Text(
                    text = "This invite link is invalid or corrupted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0x73FFFFFF),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0x14FFFFFF))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.displayName.take(2).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Add ${state.displayName}?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Text(
                text = "PUBLIC KEY",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = Color(0x40FFFFFF)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = state.publicKey.take(24) + "...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "SAFETY NUMBER",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = Color(0x40FFFFFF)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0DFFFFFF))
                    .padding(Spacing.lg)
            ) {
                Text(
                    text = state.safetyNumber,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 2.sp,
                    lineHeight = 28.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = "Compare this number with ${state.displayName} in person or over a trusted channel. If it doesn't match, someone may be impersonating them.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x73FFFFFF)
            )

            if (state.hasNostr) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "Reachable over the internet relay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0x804ADE80)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxl))

            Button(
                onClick = { viewModel.confirm() },
                enabled = !state.isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A0A0A)
                )
            ) {
                Text(
                    text = "Add Contact",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedButton(
                onClick = onBack,
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
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(Spacing.xxxl))
        }
    }
}
