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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            if (total == 0 || lastVisible >= total - 2) {
                listState.animateScrollToItem(messages.size - 1)
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

            if (messages.isEmpty()) {
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
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                        // Day separator whenever the calendar day changes (D12)
                        val prev = if (index > 0) messages[index - 1] else null
                        if (prev == null || !isSameDay(prev.timestamp, message.timestamp)) {
                            DateSeparator(timestamp = message.timestamp)
                        }
                        MessageBubble(
                            message = message,
                            isMine = message.senderPublicKey == myPublicKey,
                            onDelete = { viewModel.deleteMessage(message.id) },
                            onCancel = { viewModel.cancelMessage(message.id) },
                            onRetry = { viewModel.retryMessage(message.id) }
                        )
                    }
                }
            }

            InputBar(
                textInput = textInput,
                isRecording = isRecording,
                richEnabled = reachability != ChatReachability.OFFLINE,
                onTextChange = { if (it.length <= 5000) textInput = it },
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendTextMessage(textInput.trim())
                        textInput = ""
                    }
                },
                onAttachFile = { filePickerLauncher.launch("*/*") },
                onToggleRecord = { viewModel.toggleVoiceRecording() }
            )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OffneticColors.surfaceScrim
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

            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
                Text(
                    text = contactName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (targetColor, statusLabel) = when (reachability) {
                        ChatReachability.LOCAL -> OffneticColors.accentGreen to stringResource(R.string.chat_status_nearby)
                        ChatReachability.INTERNET_RELAY -> OffneticColors.accentBlue to stringResource(R.string.chat_status_internet)
                        ChatReachability.OFFLINE -> OffneticColors.textHint to stringResource(R.string.chat_status_offline)
                    }
                    val dotColor by animateColorAsState(targetColor, animationSpec = tween(300), label = "dot")
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    AnimatedContent(targetState = statusLabel, label = "status") { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = dotColor
                    )
                    }
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
    isRecording: Boolean,
    richEnabled: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onToggleRecord: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachFile, enabled = richEnabled) {
                Text(
                    text = "+",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = if (richEnabled) OffneticColors.textMuted else OffneticColors.textHint
                )
            }

            IconButton(onClick = onToggleRecord, enabled = richEnabled || isRecording) {
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (isRecording) OffneticColors.danger else if (richEnabled) OffneticColors.textMuted else OffneticColors.textHint,
                            radius = size.minDimension / 2
                        )
                    }
                }
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
    onDelete: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val backgroundColor = if (isMine) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val alignment = if (isMine) Alignment.End else Alignment.Start
    var showMenu by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (isMine) 24.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 24.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
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
                            val context = LocalContext.current
                            var isPlaying by remember { mutableStateOf(false) }
                            var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

                            DisposableEffect(message.id) {
                                onDispose {
                                    mediaPlayer?.release()
                                }
                            }

                            Row(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                    if (isPlaying) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        val path = message.attachmentPath
                                        if (path != null) {
                                            // Player is tracked before configuration so a throw
                                            // from prepare()/start() can release it (D1)
                                            var mp: android.media.MediaPlayer? = null
                                            try {
                                                mp = android.media.MediaPlayer()
                                                if (path.startsWith("content://")) {
                                                    mp.setDataSource(context, android.net.Uri.parse(path))
                                                } else {
                                                    mp.setDataSource(path)
                                                }
                                                mp.prepare()
                                                mp.setOnCompletionListener {
                                                    it.release()
                                                    mediaPlayer = null
                                                    isPlaying = false
                                                }
                                                mp.start()
                                                mediaPlayer = mp
                                                isPlaying = true
                                            } catch (_: Exception) {
                                                mp?.release()
                                                Toast.makeText(context, context.getString(R.string.chat_cannot_play_voice), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = { showMenu = true }
                            ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = if (isPlaying) OffneticColors.danger else OffneticColors.accentGreen,
                                            radius = size.minDimension / 2
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = OffneticColors.textSubtle
                            )
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
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (message.type == Message.TYPE_TEXT) {
                    val clipboard = LocalClipboardManager.current
                    val context = LocalContext.current
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.action_copy), style = MaterialTheme.typography.labelLarge, color = Color.White)
                        },
                        onClick = {
                            showMenu = false
                            clipboard.setText(AnnotatedString(message.content))
                            Toast.makeText(context, context.getString(R.string.chat_message_copied), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.chat_delete_for_me), style = MaterialTheme.typography.labelLarge, color = OffneticColors.danger)
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
                if (isMine && message.deliveryState == MessageDeliveryState.SAVED && message.type != Message.TYPE_CANCELLED) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.chat_cancel_sending), style = MaterialTheme.typography.labelLarge, color = OffneticColors.danger)
                        },
                        onClick = {
                            showMenu = false
                            onCancel()
                        }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
        ) {
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = OffneticColors.textHint
            )
            if (isMine && message.type != Message.TYPE_CANCELLED && message.type != Message.TYPE_SYSTEM) {
                Spacer(modifier = Modifier.width(Spacing.xs))
                val (statusColor, statusLabel) = when (message.deliveryState) {
                    MessageDeliveryState.SAVED -> OffneticColors.textHint to stringResource(R.string.chat_delivery_saved)
                    MessageDeliveryState.SENT_LOCAL -> OffneticColors.textSubtle to stringResource(R.string.chat_delivery_sending)
                    MessageDeliveryState.SENT_RELAY -> OffneticColors.textSubtle to stringResource(R.string.chat_delivery_relayed)
                    MessageDeliveryState.DELIVERED -> OffneticColors.textStrong to stringResource(R.string.chat_delivery_delivered)
                    MessageDeliveryState.READ -> OffneticColors.accentGreen to stringResource(R.string.chat_delivery_seen)
                    MessageDeliveryState.FAILED -> OffneticColors.danger to stringResource(R.string.chat_delivery_failed)
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }
        }
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
