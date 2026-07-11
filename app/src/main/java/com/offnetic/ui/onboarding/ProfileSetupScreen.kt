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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.R
import com.offnetic.ui.theme.OffneticColors
import com.offnetic.ui.theme.Spacing

private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{2,24}$")
private const val USERNAME_MAX_LENGTH = 24

@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    val viewModel: ProfileSetupViewModel = hiltViewModel()
    val saveState by viewModel.saveState.collectAsState()

    val trimmed = username.trim()
    val isValid = trimmed.matches(USERNAME_REGEX)
    val hasContent = trimmed.length >= 2
    val showError = hasContent && !isValid
    val saving = saveState == ProfileSaveState.Saving
    val canProceed = isValid && !saving

    // Navigate only after the profile row is confirmed written (O2)
    LaunchedEffect(saveState) {
        if (saveState == ProfileSaveState.Saved) onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NoiseOverlay()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xxl),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.profile_setup_tag),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.5.sp,
                color = OffneticColors.textDisabled
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = stringResource(R.string.profile_setup_question),
                style = MaterialTheme.typography.displayLarge,
                color = OffneticColors.textPrimary
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = stringResource(R.string.profile_setup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = OffneticColors.textFaint
            )

            Spacer(modifier = Modifier.height(Spacing.xxxl))

            Text(
                text = stringResource(R.string.profile_setup_username_label),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = OffneticColors.textDisabled
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(USERNAME_MAX_LENGTH) },
                placeholder = {
                    Text(
                        stringResource(R.string.profile_setup_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OffneticColors.textHint
                    )
                },
                isError = showError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OffneticColors.textPrimary,
                    unfocusedTextColor = OffneticColors.textPrimary,
                    cursorColor = OffneticColors.textPrimary,
                    focusedBorderColor = if (showError) OffneticColors.danger else OffneticColors.textHint,
                    unfocusedBorderColor = if (showError) OffneticColors.danger else OffneticColors.surfaceRaised,
                    focusedContainerColor = OffneticColors.surfaceCard,
                    unfocusedContainerColor = OffneticColors.surfaceCard
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                supportingText = {
                    Column {
                        if (showError) {
                            Text(
                                text = stringResource(R.string.profile_setup_invalid),
                                style = MaterialTheme.typography.bodySmall,
                                color = OffneticColors.danger
                            )
                        } else {
                            // Live counter makes the max-length cap visible instead of
                            // silently swallowing pasted text (O19)
                            Text(
                                text = stringResource(R.string.profile_setup_rules) + "  ·  ${username.length}/$USERNAME_MAX_LENGTH",
                                style = MaterialTheme.typography.bodySmall,
                                color = OffneticColors.textGhost
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.profile_setup_permanent),
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFFF59E0B)
                        )
                        if (saveState == ProfileSaveState.Failed) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = stringResource(R.string.profile_setup_save_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = OffneticColors.danger
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.saveProfile(trimmed) },
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = OffneticColors.surfaceBubble,
                    disabledContentColor = OffneticColors.textHint
                )
            ) {
                Text(
                    text = if (saving) stringResource(R.string.loading) else stringResource(R.string.profile_setup_enter),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xxxl))
        }
    }
}
