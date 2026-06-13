package com.offnetic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.ui.theme.FontFamilySyne

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onBack) {
                    Text("← Back", fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                }
            }
            SettingsContent()
        }
    }
}

@Composable
fun SettingsContent(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val proximityPings by viewModel.proximityPingsEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Settings",
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader("Proximity Pings")
        ToggleRow(
            title = "Ping Notifications",
            subtitle = "Notify when trusted contacts are nearby",
            checked = proximityPings,
            onToggle = { viewModel.setProximityPingsEnabled(it) }
        )
        Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontFamily = FontFamilySyne,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = Color(0x40FFFFFF),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            Text(subtitle, fontFamily = FontFamilySyne, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0x40FFFFFF))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0A0A0A),
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0x73FFFFFF),
                uncheckedTrackColor = Color(0x14FFFFFF)
            )
        )
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = FontFamilySyne, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            Text(subtitle, fontFamily = FontFamilySyne, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0x40FFFFFF))
        }
        Text("→", fontFamily = FontFamilySyne, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0x40FFFFFF))
    }
}
