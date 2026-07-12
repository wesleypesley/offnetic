package com.offnetic.ui.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.offnetic.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.domain.model.ChatReachability
import com.offnetic.domain.model.Message
import com.offnetic.domain.model.MessageDeliveryState
import com.offnetic.ui.theme.OffneticColors
import com.offnetic.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@Composable
fun ChatScreen(
    onBack: () -> Unit = {},
    onCall: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val contactName by viewModel.contactName.collectAsState()
    val reachability by viewModel.reachability.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val myPublicKey by viewModel.myPublicKey.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val voicePlayback by viewModel.voicePlayback.collectAsState()
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { viewModel.setActive(); viewModel.markAsRead() }
                Lifecycle.Event.ON_PAUSE -> viewModel.clearActive()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearActive()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendFile(it) }
    }

    LaunchedEffect(messages.size, isPeerTyping) {
        if (messages.isNotEmpty()) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total == 0 || lastVisible >= total - 2) {
                // The typing bubble is one extra item past the last message
                listState.animateScrollToItem(messages.size - 1 + if (isPeerTyping) 1 else 0)
            }
        }
    }

    // Load an older page when the user scrolls to the top of the window (D4).
    // Items are keyed by message id, so LazyColumn keeps the scroll anchored
    // when the older page prepends.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                if (firstVisible == 0) viewModel.loadOlderMessages()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .statusBarsPadding()
    ) {
        NoiseOverlay()

        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                contactName = contactName,
                reachability = reachability,
                onBack = onBack,
                onCall = { onCall(viewModel.contactPublicKey) }
            )

            if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OffneticColors.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OffneticColors.dangerSurface)
                        .padding(Spacing.sm)
                )
            }
            if (uiState.isLoading) {
                // Shimmer placeholder while the first Room emission is pending (feature #2)
                LoadingPlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else if (messages.isEmpty()) {
                // Empty state instead of a blank void (D17)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chat_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OffneticColors.textHint
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.lg),
                        state = listState
                    ) {
                        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                            // Day separator whenever the calendar day changes (D12)
                            val prev = if (index > 0) messages[index - 1] else null
                            val next = if (index < messages.lastIndex) messages[index + 1] else null
                            val newDay = prev == null || !isSameDay(prev.timestamp, message.timestamp)
                            if (newDay) {
                                DateSeparator(timestamp = message.timestamp)
                            }
                            // Same sender within 5 min forms a visual group; breaks on
                            // sender change, gap, or date boundary (feature #1)
                            val groupedWithPrev = !newDay && prev != null &&
                                prev.senderPublicKey == message.senderPublicKey &&
                                message.timestamp - prev.timestamp < GROUP_WINDOW_MS
                            val groupedWithNext = next != null &&
                                next.senderPublicKey == message.senderPublicKey &&
                                next.timestamp - message.timestamp < GROUP_WINDOW_MS &&
                                isSameDay(message.timestamp, next.timestamp)
                            MessageBubble(
                                message = message,
                                isMine = message.senderPublicKey == myPublicKey,
                                isFirstInGroup = !groupedWithPrev,
                                isLastInGroup = !groupedWithNext,
                                onDelete = { viewModel.deleteMessage(message.id) },
                                onCancel = { viewModel.cancelMessage(message.id) },
                                onRetry = { viewModel.retryMessage(message.id) },
                                onReply = { viewModel.startReply(message) },
                                voicePlayback = voicePlayback,
                                onToggleVoicePlay = {
                                    message.attachmentPath?.let { viewModel.toggleVoicePlayback(message.id, it) }
                                },
                                onSeekVoice = { fraction -> viewModel.seekVoicePlayback(message.id, fraction) }
                            )
                        }
                        // Animated-dots bubble styled like their messages (feature #6)
                        if (isPeerTyping) {
                            item(key = "typing-indicator") {
                                TypingBubble()
                            }
                        }
                    }

                    // Floating ↓ when the keyboard is open while scrolled up (feature #7)
                    val density = LocalDensity.current
                    val imeVisible = WindowInsets.ime.getBottom(density) > 0
                    val isScrolledUp by remember {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && lastVisible < info.totalItemsCount - 1
                        }
                    }
                    if (imeVisible && isScrolledUp) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.md)
                                .size(36.dp)
                                .clickable {
                                    scope.launch { listState.animateScrollToItem(messages.size - 1) }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.cd_scroll_to_bottom),
                                    tint = OffneticColors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            val replyingTo by viewModel.replyingTo.collectAsState()
            val recordState by viewModel.recordState.collectAsState()
            val recordElapsedMs by viewModel.recordElapsedMs.collectAsState()
            val liveWaveform by viewModel.liveWaveform.collectAsState()
            val haptics = LocalHapticFeedback.current
            val focusManager = LocalFocusManager.current

            if (recordState != RecordState.Idle) {
                // Recording replaces the input area entirely (feature #5)
                RecordingOverlay(
                    isLocked = recordState == RecordState.Locked,
                    elapsedMs = recordElapsedMs,
                    waveform = liveWaveform,
                    onCancel = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.cancelVoiceRecording()
                    },
                    onSend = { viewModel.finishVoiceRecording() }
                )
            } else {
                replyingTo?.let { reply ->
                    ReplyPreviewBar(
                        reply = reply,
                        isMine = reply.senderPublicKey == myPublicKey,
                        contactName = contactName,
                        onDismiss = { viewModel.clearReply() }
                    )
                }

                InputBar(
                    textInput = textInput,
                    richEnabled = reachability != ChatReachability.OFFLINE,
                    onTextChange = {
                        if (it.length <= 5000) {
                            // Signal typing only when text grows — paste of full message or
                            // deletion shouldn't ping the peer (feature #6)
                            if (it.length > textInput.length && it.isNotBlank()) viewModel.onTyping()
                            textInput = it
                        }
                    },
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendTextMessage(textInput.trim())
                            textInput = ""
                            // Sending always returns to the bottom, even if scrolled up;
                            // the messages.size effect then follows the new row (feature #7)
                            if (messages.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(messages.size - 1) }
                            }
                        }
                    },
                    onAttachFile = { filePickerLauncher.launch("*/*") },
                    onRecordStart = {
                        // Keyboard dismisses when recording starts (feature #5)
                        focusManager.clearFocus()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startVoiceRecording()
                    },
                    onRecordLock = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.lockVoiceRecording()
                    },
                    onRecordCancel = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.cancelVoiceRecording()
                    },
                    onRecordRelease = { viewModel.finishVoiceRecording() }
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    contactName: String,
    reachability: ChatReachability,
    onBack: () -> Unit,
    onCall: () -> Unit
) {
    // Solid surface — the old translucent scrim showed messages bleeding through
    // while scrolling (design polish)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Icon-only status: Bluetooth = direct P2P, globe = internet relay,
            // nothing = offline. Absence is the signal (design polish)
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contactName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                when (reachability) {
                    ChatReachability.LOCAL -> {
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = stringResource(R.string.chat_status_nearby),
                            tint = OffneticColors.accentBlue,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    ChatReachability.INTERNET_RELAY -> {
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = stringResource(R.string.chat_status_internet),
                            tint = OffneticColors.accentBlue,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    ChatReachability.OFFLINE -> Unit
                }
            }

            IconButton(onClick = onCall, enabled = reachability != ChatReachability.OFFLINE) {
                Icon(
                    painter = painterResource(R.drawable.ic_phone_outlined),
                    contentDescription = stringResource(R.string.cd_call),
                    tint = if (reachability != ChatReachability.OFFLINE) OffneticColors.iconMuted else OffneticColors.textHint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    textInput: String,
    richEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordLock: () -> Unit,
    onRecordCancel: () -> Unit,
    onRecordRelease: () -> Unit
) {
    // surfaceVariant separates the input zone from the message area, which sits
    // on `background`/`surface` (design polish)
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachFile, enabled = richEnabled) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_attach),
                    tint = if (richEnabled) OffneticColors.textMuted else OffneticColors.textHint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Hold to record; slide left to cancel, slide up to lock hands-free (feature #5)
            val density = LocalDensity.current
            val cancelThresholdPx = with(density) { 96.dp.toPx() }
            val lockThresholdPx = with(density) { 72.dp.toPx() }
            var dragAccum by remember { mutableStateOf(Offset.Zero) }
            var gestureSettled by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(richEnabled) {
                        if (!richEnabled) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragAccum = Offset.Zero
                                gestureSettled = false
                                onRecordStart()
                            },
                            onDrag = { change, delta ->
                                change.consume()
                                if (!gestureSettled) {
                                    dragAccum += delta
                                    if (dragAccum.x < -cancelThresholdPx) {
                                        gestureSettled = true
                                        onRecordCancel()
                                    } else if (dragAccum.y < -lockThresholdPx) {
                                        gestureSettled = true
                                        onRecordLock()
                                    }
                                }
                            },
                            onDragEnd = { if (!gestureSettled) onRecordRelease() },
                            onDragCancel = { if (!gestureSettled) onRecordCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.cd_record_voice),
                    tint = if (richEnabled) OffneticColors.textMuted else OffneticColors.textHint,
                    modifier = Modifier.size(20.dp)
                )
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.chat_input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OffneticColors.textHint
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OffneticColors.textHint,
                    unfocusedBorderColor = OffneticColors.surfaceRaised,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedContainerColor = OffneticColors.surfaceCard,
                    unfocusedContainerColor = OffneticColors.surfaceCard
                )
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            IconButton(
                onClick = onSend,
                enabled = textInput.isNotBlank()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.cd_send),
                    tint = if (textInput.isNotBlank()) Color.White else OffneticColors.textHint
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    onDelete: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onReply: () -> Unit = {},
    voicePlayback: VoicePlaybackState = VoicePlaybackState(),
    onToggleVoicePlay: () -> Unit = {},
    onSeekVoice: (Float) -> Unit = {}
) {
    // Color asymmetry: mine gets the navy tint, theirs neutral gray — sender is
    // recognisable before reading (design polish)
    val backgroundColor = if (isMine) OffneticColors.bubbleMine else MaterialTheme.colorScheme.surfaceContainerHighest
    val alignment = if (isMine) Alignment.End else Alignment.Start
    var showMenu by remember { mutableStateOf(false) }

    // Grouped messages flatten the corners on their grouped edge; only the last
    // message of a group keeps the tail (feature #1)
    val shape = RoundedCornerShape(
        topStart = if (isMine) 24.dp else if (isFirstInGroup) 24.dp else 6.dp,
        topEnd = if (!isMine) 24.dp else if (isFirstInGroup) 24.dp else 6.dp,
        bottomStart = if (isMine) 24.dp else if (isLastInGroup) 4.dp else 6.dp,
        bottomEnd = if (!isMine) 24.dp else if (isLastInGroup) 4.dp else 6.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirstInGroup) Spacing.sm else 2.dp),
        horizontalAlignment = alignment
    ) {
        Box {
            Surface(
                shape = shape,
                color = if (message.type == Message.TYPE_CANCELLED) backgroundColor.copy(alpha = 0.5f) else backgroundColor,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .then(
                        if (!isMine) Modifier.border(0.5.dp, OffneticColors.surfaceBubble, shape)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    // Embedded reply quote — self-contained, no DB lookup (feature #4)
                    if (message.quotedPreview != null) {
                        QuotedBlock(
                            sender = message.quotedSender ?: "",
                            preview = message.quotedPreview ?: ""
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                    }
                    when (message.type) {
                        Message.TYPE_CANCELLED -> {
                            Text(
                                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                                color = OffneticColors.textSubtle
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(
                                    text = stringResource(R.string.action_retry),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OffneticColors.accentGreen,
                                    modifier = Modifier.clickable { onRetry() }
                                )
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OffneticColors.danger,
                                    modifier = Modifier.clickable { onDelete() }
                                )
                            }
                        }
                        Message.TYPE_TEXT -> {
                            LinkifiedText(
                                text = message.content,
                                onLongPress = { showMenu = true }
                            )
                        }
                        Message.TYPE_VOICE_NOTE -> {
                            // Playback state lives in the ViewModel so audio survives
                            // scrolling this bubble out of composition (feature #5)
                            val isActive = voicePlayback.messageId == message.id
                            val playing = isActive && voicePlayback.isPlaying
                            val fraction = if (isActive && voicePlayback.durationMs > 0) {
                                voicePlayback.positionMs / voicePlayback.durationMs.toFloat()
                            } else 0f

                            Row(
                                modifier = Modifier.combinedClickable(
                                    onClick = { if (message.attachmentPath != null) onToggleVoicePlay() },
                                    onLongClick = { showMenu = true }
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                                    tint = OffneticColors.accentGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column {
                                    WaveformSeekBar(
                                        samples = message.waveformData?.map { (it.toInt() and 0xFF) / 255f },
                                        progress = fraction,
                                        enabled = isActive,
                                        onSeek = onSeekVoice,
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(24.dp)
                                    )
                                    Text(
                                        text = if (isActive) {
                                            "${formatPlaybackTime(voicePlayback.positionMs)} / ${formatPlaybackTime(voicePlayback.durationMs)}"
                                        } else {
                                            message.content.removePrefix("Voice note").trim()
                                        },
                                        fontSize = 10.sp,
                                        color = OffneticColors.textSubtle
                                    )
                                }
                            }
                        }
                        Message.TYPE_FILE, Message.TYPE_IMAGE, Message.TYPE_VIDEO -> {
                            val context = LocalContext.current
                            val path = message.attachmentPath
                            val fileName = message.content.removePrefix("File: ")
                            val mimeType = inferMimeType(fileName)

                            Column {
                                when {
                                    mimeType.startsWith("image/") && path != null -> {
                                        ImageThumbnail(
                                            context = context,
                                            path = path,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                        ) {
                                            openFile(context, path, mimeType)
                                        }
                                    }
                                    mimeType.startsWith("video/") && path != null -> {
                                        VideoThumbnail(
                                            context = context,
                                            path = path,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                        ) {
                                            openFile(context, path, mimeType)
                                        }
                                    }
                                    else -> {
                                        DocAttachment(
                                            fileName = fileName,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (path != null) openFile(context, path, mimeType)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OffneticColors.textSubtle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Message.TYPE_SYSTEM -> {
                            Text(
                                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                                color = OffneticColors.textSubtle
                            )
                        }
                    }

                    // Timestamp + delivery dot live inside the bubble, bottom-right,
                    // and only on the last message of a group (design polish)
                    if (isLastInGroup && message.type != Message.TYPE_CANCELLED && message.type != Message.TYPE_SYSTEM) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = Spacing.xxs)
                        ) {
                            Text(
                                text = formatTime(message.timestamp),
                                fontSize = 10.sp,
                                color = OffneticColors.textSubtle
                            )
                            if (isMine) {
                                when (message.deliveryState) {
                                    MessageDeliveryState.DELIVERED -> DeliveryDot(OffneticColors.textStrong)
                                    MessageDeliveryState.READ -> DeliveryDot(OffneticColors.accentGreen)
                                    MessageDeliveryState.FAILED -> {
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Icon(
                                            imageVector = Icons.Filled.ErrorOutline,
                                            contentDescription = stringResource(R.string.chat_action_retry_failed),
                                            tint = OffneticColors.danger,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { onRetry() }
                                        )
                                    }
                                    // SAVED / SENT_LOCAL / SENT_RELAY: nothing — sent is the default
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }

            if (showMenu) {
                val haptics = LocalHapticFeedback.current
                val keyboard = LocalSoftwareKeyboardController.current
                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    // Bar must not render behind the IME; haptic confirms the long-press
                    keyboard?.hide()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                Popup(
                    alignment = if (isMine) Alignment.TopEnd else Alignment.TopStart,
                    offset = IntOffset(0, with(LocalDensity.current) { (-60).dp.roundToPx() }),
                    onDismissRequest = { showMenu = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    MessageActionBar(
                        onCopy = if (message.type == Message.TYPE_TEXT) {
                            {
                                clipboard.setText(AnnotatedString(message.content))
                                Toast.makeText(context, context.getString(R.string.chat_message_copied), Toast.LENGTH_SHORT).show()
                            }
                        } else null,
                        onReply = if (message.type != Message.TYPE_SYSTEM) onReply else null,
                        onRetry = if (message.type == Message.TYPE_CANCELLED || message.deliveryState == MessageDeliveryState.FAILED) onRetry else null,
                        onCancel = if (isMine && message.deliveryState == MessageDeliveryState.SAVED && message.type != Message.TYPE_CANCELLED) onCancel else null,
                        onDelete = onDelete,
                        onDismiss = { showMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryDot(color: Color) {
    Spacer(modifier = Modifier.width(Spacing.xs))
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(color, CircleShape)
    )
}

// iMessage-style horizontal pill with icon+label actions (feature #0)
@Composable
private fun MessageActionBar(
    onCopy: (() -> Unit)?,
    onReply: (() -> Unit)? = null,
    onRetry: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (onCopy != null) {
                ActionBarItem(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy), OffneticColors.textPrimary) {
                    onCopy()
                    onDismiss()
                }
            }
            if (onReply != null) {
                ActionBarItem(Icons.AutoMirrored.Filled.Reply, stringResource(R.string.chat_reply), OffneticColors.textPrimary) {
                    onReply()
                    onDismiss()
                }
            }
            if (onRetry != null) {
                ActionBarItem(Icons.Filled.Refresh, stringResource(R.string.action_retry), OffneticColors.accentGreen) {
                    onRetry()
                    onDismiss()
                }
            }
            if (onCancel != null) {
                ActionBarItem(Icons.Filled.Close, stringResource(R.string.action_cancel), OffneticColors.danger) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCancel()
                    onDismiss()
                }
            }
            ActionBarItem(Icons.Outlined.Delete, stringResource(R.string.action_delete), OffneticColors.danger) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDelete()
                onDismiss()
            }
        }
    }
}

@Composable
private fun ActionBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(text = label, fontSize = 10.sp, color = tint)
    }
}

@Composable
private fun ImageThumbnail(
    context: android.content.Context,
    path: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    DisposableEffect(path) {
        onDispose {
            bitmap?.recycle()
            bitmap = null
        }
    }

    LaunchedEffect(path) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                // Two-pass decode: read bounds, then sample down to ~1280px on the long
                // edge — the fixed inSampleSize=2 under-sampled large camera photos and
                // over-sampled small ones (D37)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                decodeAt(context, path, bounds)
                val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (longEdge / (sample * 2) >= 1280) sample *= 2
                decodeAt(context, path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    val bmp = bitmap
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OffneticColors.surfaceCard)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!failed) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textDisabled,
                modifier = Modifier.padding(Spacing.xl)
            )
        } else {
            Text(
                text = stringResource(R.string.chat_preview_failed),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textHint,
                modifier = Modifier.padding(Spacing.xl)
            )
        }
    }
}

@Composable
private fun VideoThumbnail(
    context: android.content.Context,
    path: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var frame by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    DisposableEffect(path) {
        onDispose {
            frame?.recycle()
            frame = null
        }
    }

    LaunchedEffect(path) {
        val retriever = MediaMetadataRetriever()
        try {
            frame = withContext(Dispatchers.IO) {
                if (path.startsWith("content://")) {
                    retriever.setDataSource(context, android.net.Uri.parse(path))
                } else {
                    retriever.setDataSource(path)
                }
                retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) {
            failed = true
        } finally {
            retriever.release()
        }
    }

    val bmp = frame
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OffneticColors.surfaceCard)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_video),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(OffneticColors.scrimDark),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val trianglePath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(trianglePath, Color.White)
                }
            }
        } else if (!failed) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textDisabled,
                modifier = Modifier.padding(Spacing.xl)
            )
        } else {
            Text(
                text = stringResource(R.string.chat_preview_failed),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textHint,
                modifier = Modifier.padding(Spacing.xl)
            )
        }
    }
}

@Composable
private fun DocAttachment(
    fileName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OffneticColors.surfaceCard)
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = OffneticColors.surfaceRaised
        ) {
            Box(contentAlignment = Alignment.Center) {
            Text(
                text = fileName.substringAfterLast('.', "?").take(3).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = OffneticColors.textMuted
            )
            }
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.chat_tap_to_open),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textHint
            )
        }
    }
}

private fun openFile(context: android.content.Context, path: String, mimeType: String) {
    try {
        val uri: android.net.Uri = if (path.startsWith("content://")) {
            android.net.Uri.parse(path)
        } else {
            val file = java.io.File(path)
            if (!file.exists()) {
                Toast.makeText(context, context.getString(R.string.chat_file_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, context.getString(R.string.chat_no_app_for_file), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.chat_cannot_open_file), Toast.LENGTH_SHORT).show()
    }
}

private fun inferMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "rar" -> "application/x-rar-compressed"
        "7z" -> "application/x-7z-compressed"
        "apk" -> "application/vnd.android.package-archive"
        "txt" -> "text/plain"
        "html" -> "text/html"
        "csv" -> "text/csv"
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "heic", "heif" -> "image/heic"
        "avi" -> "video/x-msvideo"
        else -> "application/octet-stream"
    }
}

private fun decodeAt(context: android.content.Context, path: String, opts: BitmapFactory.Options): Bitmap? {
    return if (path.startsWith("content://")) {
        context.contentResolver.openInputStream(android.net.Uri.parse(path))
            ?.use { BitmapFactory.decodeStream(it, null, opts) }
    } else {
        BitmapFactory.decodeFile(path, opts)
    }
}

private val noisePoints: List<Pair<Float, Float>> = run {
    val rng = Random(42)
    (0 until 300).map { rng.nextFloat() to rng.nextFloat() }
}

// Feature #8 considered rasterizing this to a Bitmap, but a full-screen ARGB buffer
// costs ~10MB for a barely-visible texture. Compose already caches the draw ops in
// the node's display list — the 300 circles are only re-recorded when this layer is
// invalidated, not per frame — so the vector draw is the cheaper option.
@Composable
private fun NoiseOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        for ((rx, ry) in noisePoints) {
            drawCircle(
                Color.White,
                radius = 1.0f,
                center = Offset(rx * size.width, ry * size.height),
                alpha = 0.018f
            )
        }
    }
}

private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(timestamp: Long): String = timeFormatter.format(Date(timestamp))

// Messages from the same sender within this window merge into one group (feature #1)
private const val GROUP_WINDOW_MS = 5L * 60 * 1000

// Recording UI that replaces the input bar while holding the mic (feature #5)
@Composable
private fun RecordingOverlay(
    isLocked: Boolean,
    elapsedMs: Long,
    waveform: List<Float>,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingRecordDot()
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = formatPlaybackTime(elapsedMs.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textStrong
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            LiveWaveform(
                samples = waveform,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            if (isLocked) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_discard_recording),
                        tint = OffneticColors.danger,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onSend) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.cd_send_voice),
                        tint = OffneticColors.accentGreen
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.chat_slide_to_cancel),
                    style = MaterialTheme.typography.bodySmall,
                    color = OffneticColors.textMuted
                )
            }
        }
    }
}

@Composable
private fun PulsingRecordDot() {
    val transition = rememberInfiniteTransition(label = "recDot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "recDotAlpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(OffneticColors.danger.copy(alpha = alpha), CircleShape)
    )
}

// Rolling amplitude bars while recording (feature #5)
@Composable
private fun LiveWaveform(samples: List<Float>, modifier: Modifier = Modifier) {
    val bars = samples.takeLast(40)
    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val slot = size.width / 40
        bars.forEachIndexed { i, amp ->
            val h = amp.coerceAtLeast(0.06f) * size.height
            drawRoundRect(
                color = OffneticColors.textStrong,
                topLeft = Offset(i * slot + slot * 0.2f, (size.height - h) / 2f),
                size = Size(slot * 0.6f, h),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

// Waveform with played/unplayed split and tap/drag seeking; falls back to a plain
// progress track for messages recorded before waveforms existed (feature #5)
@Composable
private fun WaveformSeekBar(
    samples: List<Float>?,
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onTap = { pos ->
                onSeek((pos.x / size.width).coerceIn(0f, 1f))
            })
        }
    ) {
        val playedColor = OffneticColors.accentGreen
        val restColor = OffneticColors.textHint
        if (samples.isNullOrEmpty()) {
            // Fallback: thin progress track
            val y = size.height / 2f
            drawLine(restColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
            if (progress > 0f) {
                drawLine(playedColor, Offset(0f, y), Offset(size.width * progress, y), strokeWidth = 3f)
            }
            return@Canvas
        }
        // Downsample to at most 40 bars
        val barCount = minOf(40, samples.size)
        val step = samples.size / barCount.toFloat()
        val slot = size.width / barCount
        for (i in 0 until barCount) {
            val amp = samples[(i * step).toInt().coerceAtMost(samples.lastIndex)]
            val h = amp.coerceAtLeast(0.08f) * size.height
            val barFraction = (i + 0.5f) / barCount
            drawRoundRect(
                color = if (barFraction <= progress) playedColor else restColor,
                topLeft = Offset(i * slot + slot * 0.2f, (size.height - h) / 2f),
                size = Size(slot * 0.6f, h),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

private fun formatPlaybackTime(ms: Int): String {
    val totalSec = ms / 1000
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

// Their-style bubble containing three pulsing dots (feature #6). Styling matches
// MessageBubble's "theirs" appearance so an arriving real message causes no jump.
@Composable
private fun TypingBubble() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.border(
                0.5.dp,
                OffneticColors.surfaceBubble,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 24.dp)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    TypingDot(index)
                }
            }
        }
    }
}

@Composable
private fun TypingDot(index: Int) {
    val transition = rememberInfiniteTransition(label = "typingDot$index")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = index * 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typingDotAlpha$index"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), CircleShape)
    )
}

// Quoted message rendered above the reply content inside the bubble (feature #4)
@Composable
private fun QuotedBlock(sender: String, preview: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OffneticColors.surfaceCard)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 36.dp)
                .background(OffneticColors.accentBlue)
        )
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            if (sender.isNotEmpty()) {
                Text(
                    text = sender,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OffneticColors.accentBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Quote preview pinned above the input bar while composing a reply (feature #4)
@Composable
private fun ReplyPreviewBar(
    reply: Message,
    isMine: Boolean,
    contactName: String,
    onDismiss: () -> Unit
) {
    val senderLabel = if (isMine) stringResource(R.string.chat_reply_you) else contactName
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, top = Spacing.sm, bottom = 0.dp, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(OffneticColors.accentBlue)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_replying_to, senderLabel),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OffneticColors.accentBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (reply.type) {
                        Message.TYPE_VOICE_NOTE -> "🎤 ${reply.content}"
                        Message.TYPE_FILE, Message.TYPE_IMAGE, Message.TYPE_VIDEO ->
                            "📎 ${reply.content.removePrefix("File: ")}"
                        else -> reply.content
                    }.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = OffneticColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Explicit dismiss button — tap-to-dismiss is too easy to hit while typing
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = OffneticColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Column(
        modifier = modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        repeat(4) { i ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (i % 2 == 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(width = (200 - i * 24).dp, height = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(OffneticColors.surfaceBubble.copy(alpha = alpha))
                )
            }
        }
    }
}

private val URL_REGEX = Regex("""https?://\S+""")

// Text bubble with tappable URLs (C1). Long-press is handled here too because the
// gesture detector consumes the touch stream, so the parent's combinedClickable
// would never see it.
@Composable
private fun LinkifiedText(text: String, onLongPress: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (m in URL_REGEX.findAll(text)) {
                append(text.substring(last, m.range.first))
                pushStringAnnotation("URL", m.value)
                withStyle(SpanStyle(color = OffneticColors.accentBlue, textDecoration = TextDecoration.Underline)) {
                    append(m.value)
                }
                pop()
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = OffneticColors.textBright,
        maxLines = 20,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures(
                onLongPress = { onLongPress() },
                onTap = { pos ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { link ->
                        runCatching { uriHandler.openUri(link.item) }
                    }
                }
            )
        }
    )
}

// Centered day chip between messages from different calendar days (D12)
@Composable
private fun DateSeparator(timestamp: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatDateSeparator(
                timestamp,
                stringResource(R.string.chat_today),
                stringResource(R.string.chat_yesterday)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = OffneticColors.textHint,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(OffneticColors.surfaceCard)
                .padding(horizontal = Spacing.md, vertical = Spacing.xxs)
        )
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun formatDateSeparator(timestamp: Long, today: String, yesterday: String): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(timestamp, now) -> today
        isSameDay(timestamp, now - 86_400_000L) -> yesterday
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
