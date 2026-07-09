package com.offnetic.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.ui.theme.Spacing

@Composable
fun RequestsScreen(
    onBack: () -> Unit = {},
    viewModel: RequestsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val inFlight by viewModel.inFlight.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.sm, top = Spacing.lg),
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

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "Connection Requests",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = Color(0x73FFFFFF))
                }
            } else if (state.requests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pending connection requests",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0x73FFFFFF)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl)
                ) {
                    items(state.requests, key = { it.requestId }) { request ->
                        RequestItem(
                            request = request,
                            isInFlight = request.requestId in inFlight,
                            onAccept = { viewModel.accept(request.requestId) },
                            onIgnore = { viewModel.ignore(request.requestId) }
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    item { Spacer(modifier = Modifier.navigationBarsPadding().height(Spacing.xxxl)) }
                }
            }
        }
    }
}

@Composable
private fun RequestItem(
    request: PendingRequestEntity,
    isInFlight: Boolean = false,
    onAccept: () -> Unit,
    onIgnore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0DFFFFFF))
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = request.displayName.take(2).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = request.peerNostrKey.take(12) + "…",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0x40FFFFFF)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        Button(
            onClick = onAccept,
            enabled = !isInFlight,
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0A0A0A))
        ) {
            Text("Accept", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.width(Spacing.xs))

        OutlinedButton(
            onClick = onIgnore,
            enabled = !isInFlight,
            modifier = Modifier.height(38.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0x73FFFFFF)),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0x14FFFFFF))
            )
        ) {
            Text("Ignore", style = MaterialTheme.typography.labelSmall)
        }
    }
}
