package com.offnetic.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.offnetic.ui.theme.FontFamilyIBM
import com.offnetic.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallsScreen(
    onCallClick: (String) -> Unit = {},
    viewModel: CallsViewModel = hiltViewModel()
) {
    val summaries by viewModel.callSummaries.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No calls yet",
                    color = Color(0xFF666666),
                    fontFamily = FontFamilyIBM,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(summaries, key = { it.peerPublicKey }) { summary ->
                    CallRow(
                        summary = summary,
                        onClick = { onCallClick(summary.peerPublicKey) }
                    )
                }
                item { Spacer(modifier = Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun CallRow(summary: CallSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.xxxl)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                summary.displayName.take(2).uppercase(),
                color = Color(0xFF888888),
                fontFamily = FontFamilyIBM,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(Spacing.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                summary.displayName,
                color = Color.White,
                fontFamily = FontFamilyIBM,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeIcon = when {
                    summary.lastCallDirection == 2 -> "\u2193" // missed
                    summary.lastCallDirection == 1 -> "\u2190" // incoming
                    else -> "\u2192" // outgoing
                }
                val typeColor = when (summary.lastCallDirection) {
                    2 -> Color(0xFFEF4444)
                    else -> Color(0xFF888888)
                }
                Text(
                    typeIcon,
                    color = typeColor,
                    fontFamily = FontFamilyIBM,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    formatCallType(summary),
                    color = Color(0xFF888888),
                    fontFamily = FontFamilyIBM,
                    fontSize = 13.sp
                )
            }
        }

        Text(
            formatTimestamp(summary.lastCallTimestamp),
            color = Color(0xFF666666),
            fontFamily = FontFamilyIBM,
            fontSize = 12.sp
        )
    }
}

private fun formatCallType(summary: CallSummary): String {
    val type = if (summary.lastCallType == 0) "Voice" else "Video"
    val direction = when (summary.lastCallDirection) {
        0 -> "Outgoing"
        1 -> "Incoming"
        2 -> "Missed"
        else -> ""
    }
    val duration = if (summary.lastCallDurationSeconds > 0) {
        val m = summary.lastCallDurationSeconds / 60
        val s = summary.lastCallDurationSeconds % 60
        " ${m}:${s.toString().padStart(2, '0')}"
    } else ""
    return "$type $direction$duration"
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}
