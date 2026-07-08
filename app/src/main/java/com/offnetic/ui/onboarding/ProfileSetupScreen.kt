package com.offnetic.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offnetic.ui.theme.Spacing
import androidx.hilt.navigation.compose.hiltViewModel

private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{2,24}$")

@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val viewModel: ProfileSetupViewModel = hiltViewModel()

    val trimmed = username.trim()
    val isValid = trimmed.matches(USERNAME_REGEX)
    val hasContent = trimmed.length >= 2
    val showError = hasContent && !isValid
    val canProceed = isValid

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Color(0xFF0A0A0A))
    ) {
        NoiseOverlay()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xxl),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = "SET UP PROFILE",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.5.sp,
                color = Color(0x4DFFFFFF)
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "How should people see you?",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = "Your name is only visible to trusted contacts you\u2019ve paired with.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0x59FFFFFF)
            )

            Spacer(modifier = Modifier.height(Spacing.xxxl))

            Text(
                text = "USERNAME",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = Color(0x4DFFFFFF)
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedTextField(
                value = username,
                onValueChange = { if (it.length <= 24) username = it },
                placeholder = {
                    Text(
                        "Enter a username",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0x40FFFFFF)
                    )
                },
                isError = showError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = if (showError) Color(0xFFEF4444) else Color(0x40FFFFFF),
                    unfocusedBorderColor = if (showError) Color(0xFFEF4444) else Color(0x1AFFFFFF),
                    focusedContainerColor = Color(0x0DFFFFFF),
                    unfocusedContainerColor = Color(0x0DFFFFFF)
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                supportingText = {
                    Column {
                        if (showError) {
                            Text(
                                text = "Only letters, numbers, and underscores",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFEF4444)
                            )
                        } else {
                            Text(
                                text = "Letters, numbers, and underscores only",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0x33FFFFFF)
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "\u26A0 This cannot be changed later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveProfile(trimmed)
                    onDone()
                },
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canProceed) Color.White else Color(0x14FFFFFF),
                    contentColor = if (canProceed) Color(0xFF0A0A0A) else Color(0x40FFFFFF),
                    disabledContainerColor = Color(0x14FFFFFF),
                    disabledContentColor = Color(0x40FFFFFF)
                )
            ) {
                Text(
                    text = "Enter Offnetic",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxxl))
        }
    }
}
