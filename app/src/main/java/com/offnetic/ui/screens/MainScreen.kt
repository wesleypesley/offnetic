package com.offnetic.ui.screens

import android.app.Activity
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
import com.offnetic.ui.theme.FontFamilySyne

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
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val iconColor = if (isActive) Color.White else Color(0x40FFFFFF)
        Canvas(modifier = Modifier.size(22.dp)) {
            val cw = size.width
            val ch = size.height
            when (label) {
                "Chats" -> {
                    val left = cw * 0.08f
                    val top = cw * 0.06f
                    val right = cw * 0.88f
                    val bottom = ch * 0.74f
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(cw * 0.1f, cw * 0.1f),
                        style = Stroke(width = 1.5f)
                    )
                    val tailX = left + cw * 0.28f
                    drawLine(
                        color = iconColor,
                        start = Offset(tailX - cw * 0.06f, bottom),
                        end = Offset(tailX, ch * 0.95f),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(tailX + cw * 0.06f, bottom),
                        end = Offset(tailX, ch * 0.95f),
                        strokeWidth = 1.5f
                    )
                    val lineY1 = top + cw * 0.26f
                    val lineY2 = top + cw * 0.46f
                    val lineLeft = left + cw * 0.22f
                    val lineRight = right - cw * 0.22f
                    drawLine(iconColor, Offset(lineLeft, lineY1), Offset(lineRight, lineY1), 1.3f)
                    drawLine(iconColor, Offset(lineLeft, lineY2), Offset(lineRight * 0.92f, lineY2), 1.3f)
                }
                "Calls" -> {
                    val cx = cw / 2f
                    val top = ch * 0.04f
                    val bottom = ch * 0.88f
                    val r = cw * 0.38f
                    val handleTop = ch * 0.28f
                    val handleBottom = ch * 0.78f
                    val path = Path().apply {
                        moveTo(cx - cw * 0.30f, top)
                        cubicTo(cx - r, ch * 0.15f, cx - r, ch * 0.50f, cx - cw * 0.12f, bottom)
                        cubicTo(cx, bottom, cx, handleBottom, cx, handleBottom)
                    }
                    val pathRight = Path().apply {
                        moveTo(cx + cw * 0.30f, top)
                        cubicTo(cx + r, ch * 0.15f, cx + r, ch * 0.50f, cx + cw * 0.12f, bottom)
                        cubicTo(cx, bottom, cx, handleBottom, cx, handleBottom)
                    }
                    drawPath(path, color = iconColor, style = Stroke(width = 1.5f))
                    drawPath(pathRight, color = iconColor, style = Stroke(width = 1.5f))
                    drawLine(iconColor, Offset(cx, handleTop), Offset(cx, handleBottom), 1.5f)
                    drawLine(iconColor, Offset(cx - cw * 0.12f, handleTop), Offset(cx + cw * 0.12f, handleTop), 1.5f)
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
