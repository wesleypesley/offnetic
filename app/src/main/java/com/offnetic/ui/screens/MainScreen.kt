package com.offnetic.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.offnetic.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.offnetic.service.NcapForegroundService
import com.offnetic.ui.call.CallsScreen
import com.offnetic.ui.chat.ChatListContent
import com.offnetic.ui.chat.ChatListViewModel
import com.offnetic.ui.navigation.Routes
import com.offnetic.ui.theme.FontFamilySyne
import com.offnetic.ui.theme.Spacing

@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val safeTab = selectedTab.coerceIn(0, 1)
    val chatListViewModel: ChatListViewModel = hiltViewModel()
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A),
        bottomBar = {
            BottomNavBar(
                selectedTab = safeTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (safeTab) {
                0 -> ChatListContent(
                    onChatClick = { key -> navController.navigate(Routes.chatRoute(key)) },
                    onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                    onShutdown = {
                        context.stopService(Intent(context, NcapForegroundService::class.java))
                        activity?.finish()
                    },
                    viewModel = chatListViewModel
                )
                1 -> CallsScreen(
                    onCallClick = { key -> navController.navigate(Routes.chatRoute(key)) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xE60A0A0A),
        border = BorderStroke(0.5.dp, Color(0x0FFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md, bottom = Spacing.md),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavTab(label = "Chats", isActive = selectedTab == 0, onClick = { onTabSelected(0) })
            NavTab(label = "Calls", isActive = selectedTab == 1, onClick = { onTabSelected(1) })
        }
    }
}

@Composable
private fun NavTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        val iconColor = if (isActive) Color.White else Color(0x40FFFFFF)
        Icon(
            painter = when (label) {
                "Chats" -> painterResource(R.drawable.ic_chat_bubble)
                "Calls" -> painterResource(R.drawable.ic_phone_outlined)
                else -> painterResource(R.drawable.ic_chat_bubble)
            },
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontFamily = FontFamilySyne,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) Color(0xB3FFFFFF) else Color(0x33FFFFFF)
        )
    }
}
