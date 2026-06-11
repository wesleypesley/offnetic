package com.offnetic.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offnetic.ui.theme.FontFamilySyne
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val viewModel: ProfileSetupViewModel = hiltViewModel()

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> avatarUri = uri }

    val canProceed = username.trim().length >= 2

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
                .padding(horizontal = 32.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = "SET UP PROFILE",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 2.5.sp,
                color = Color(0x4DFFFFFF)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "How should people see you?",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                color = Color.White,
                letterSpacing = (-1).sp,
                lineHeight = 40.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your name and photo are only visible to trusted contacts you've paired with.",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                color = Color(0x59FFFFFF),
                lineHeight = 25.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(26.dp))
                        .clickable { avatarPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.take(2).uppercase().ifEmpty { "?" },
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0x99FFFFFF)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Text(
                    text = "Tap to choose a photo from gallery",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color(0x4DFFFFFF)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "USERNAME",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = Color(0x4DFFFFFF)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { if (it.length <= 24) username = it },
                placeholder = {
                    Text(
                        "Enter a username",
                        fontFamily = FontFamilySyne,
                        color = Color(0x40FFFFFF)
                    )
                },
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
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                supportingText = {
                    Text(
                        text = "Letters, numbers, and underscores only",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color(0x33FFFFFF)
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.saveProfile(username.trim(), avatarUri)
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
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
