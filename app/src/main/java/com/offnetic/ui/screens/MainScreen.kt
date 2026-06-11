package com.offnetic.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.offnetic.ui.settings.SettingsContent
import com.offnetic.ui.theme.FontFamilySyne

@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val chatListViewModel: ChatListViewModel = hiltViewModel()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A),
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatListContent(
                    onChatClick = { key -> navController.navigate(Routes.chatRoute(key)) },
                    onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                    viewModel = chatListViewModel
                )
                1 -> CallsScreen(
                    onCallClick = { key -> navController.navigate(Routes.chatRoute(key)) }
                )
                2 -> SettingsContent(
                    onAccountClick = { navController.navigate(Routes.ACCOUNT) },
                    onBlockedContactsClick = { navController.navigate(Routes.BLOCKED_CONTACTS) },
                    onScoutModeToggle = { enabled ->
                        if (enabled) {
                            context.startForegroundService(Intent(context, NcapForegroundService::class.java))
                        } else {
                            context.stopService(Intent(context, NcapForegroundService::class.java))
                        }
                    }
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
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavTab(label = "Chats", isActive = selectedTab == 0, onClick = { onTabSelected(0) })
            NavTab(label = "Calls", isActive = selectedTab == 1, onClick = { onTabSelected(1) })
            NavTab(label = "Settings", isActive = selectedTab == 2, onClick = { onTabSelected(2) })
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val iconColor = if (isActive) Color.White else Color(0x40FFFFFF)
        Canvas(modifier = Modifier.size(22.dp)) {
            val cw = size.width
            val ch = size.height
            when (label) {
                "Chats" -> {
                    val left = cw * 0.1f
                    val top = cw * 0.08f
                    val right = cw * 0.9f
                    val bottom = ch * 0.85f
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(cw * 0.1f, cw * 0.1f),
                        style = Stroke(width = 1.5f)
                    )
                    val tailX = left + (right - left) * 0.5f
                    drawLine(
                        color = iconColor,
                        start = Offset(tailX - cw * 0.08f, bottom),
                        end = Offset(tailX, ch * 0.97f),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(tailX + cw * 0.08f, bottom),
                        end = Offset(tailX, ch * 0.97f),
                        strokeWidth = 1.5f
                    )
                }
                "Calls" -> {
                    val cx = cw / 2
                    val r = cw * 0.4f
                    val path = Path().apply {
                        moveTo(cx, ch * 0.05f)
                        cubicTo(cx - r, ch * 0.15f, cx - r, ch * 0.6f, cx, ch * 0.9f)
                        cubicTo(cx + r, ch * 0.6f, cx + r, ch * 0.15f, cx, ch * 0.05f)
                    }
                    drawPath(path, color = iconColor, style = Stroke(width = 1.5f))
                    drawLine(color = iconColor, start = Offset(cx, ch * 0.25f), end = Offset(cx, ch * 0.75f), strokeWidth = 1.5f)
                }
                "Settings" -> {
                    val cx = cw / 2
                    val cy = ch / 2
                    drawCircle(color = iconColor, radius = cw * 0.12f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
                    drawCircle(color = iconColor, radius = cw * 0.3f, center = Offset(cx, cy),
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cw * 0.18f, cw * 0.18f), 0f)))
                }
            }
        }
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
