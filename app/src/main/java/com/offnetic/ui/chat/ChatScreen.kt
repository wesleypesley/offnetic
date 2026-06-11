package com.offnetic.ui.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.offnetic.domain.model.Message
import com.offnetic.ui.theme.FontFamilySyne
import kotlinx.coroutines.Dispatchers
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
    val isOnline by viewModel.isContactOnline.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val myPublicKey by viewModel.myPublicKey.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.markAsRead()
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
        uri?.let { selectedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            val fileUri = if (selectedUri.scheme == "content") {
                val tempFile = java.io.File(context.cacheDir, "upload_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                "file://${tempFile.absolutePath}"
            } else {
                selectedUri.toString()
            }
            viewModel.sendFile(fileUri)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .statusBarsPadding()
    ) {
        NoiseOverlay()

        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                contactName = contactName,
                isOnline = isOnline,
                onBack = onBack,
                onCall = { onCall(viewModel.contactPublicKey) }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = message.senderPublicKey == myPublicKey
                    )
                }
            }

            InputBar(
                textInput = textInput,
                isRecording = isRecording,
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
    isOnline: Boolean,
    onBack: () -> Unit,
    onCall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xE60A0A0A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text(
                    text = "←",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = contactName,
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isOnline) Color(0xFF4ADE80) else Color(0x40FFFFFF),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        fontFamily = FontFamilySyne,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = if (isOnline) Color(0xFF4ADE80) else Color(0x40FFFFFF)
                    )
                }
            }

            IconButton(onClick = onCall) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r = size.minDimension * 0.4f
                    drawCircle(
                        color = Color(0xB3FFFFFF),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5f)
                    )
                    drawLine(
                        color = Color(0xB3FFFFFF),
                        start = Offset(cx - r * 0.6f, cy + r * 0.5f),
                        end = Offset(cx, cy + r * 1.1f),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color(0xB3FFFFFF),
                        start = Offset(cx + r * 0.6f, cy + r * 0.5f),
                        end = Offset(cx, cy + r * 1.1f),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    textInput: String,
    isRecording: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit,
    onToggleRecord: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = Color(0xFF141414)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachFile) {
                Text(
                    text = "+",
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0x73FFFFFF)
                )
            }

            IconButton(onClick = onToggleRecord) {
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = if (isRecording) Color(0xFFEF4444) else Color(0x73FFFFFF),
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
                        "Message",
                        fontFamily = FontFamilySyne,
                        color = Color(0x40FFFFFF)
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0x40FFFFFF),
                    unfocusedBorderColor = Color(0x1AFFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    containerColor = Color(0x0DFFFFFF)
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = textInput.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (textInput.isNotBlank()) Color.White else Color(0x40FFFFFF)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean
) {
    val backgroundColor = if (isMine) Color(0xFF1E1E1E) else Color(0xFF141414)
    val alignment = if (isMine) Alignment.End else Alignment.Start

    val shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (isMine) 24.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 24.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = backgroundColor,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (!isMine) Modifier.border(0.5.dp, Color(0x14FFFFFF), shape)
                    else Modifier
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                when (message.type) {
                    Message.TYPE_TEXT -> {
                        Text(
                            text = message.content,
                            fontFamily = FontFamilySyne,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = Color(0xE6FFFFFF),
                            maxLines = 20,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Message.TYPE_VOICE_NOTE -> {
                        val context = LocalContext.current
                        var isPlaying by remember { mutableStateOf(false) }
                        var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

                        DisposableEffect(Unit) {
                            onDispose {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                            }
                        }

                        Row(
                            modifier = Modifier.clickable {
                                if (isPlaying) {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                } else {
                                    val path = message.attachmentPath
                                    if (path != null) {
                                        try {
                                            val mp = android.media.MediaPlayer().apply {
                                                setDataSource(path)
                                                prepare()
                                                setOnCompletionListener {
                                                    it.release()
                                                    mediaPlayer = null
                                                    isPlaying = false
                                                }
                                                start()
                                            }
                                            mediaPlayer = mp
                                            isPlaying = true
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Cannot play voice note", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = if (isPlaying) Color(0xFFEF4444) else Color(0xFF4ADE80),
                                        radius = size.minDimension / 2
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlaying) "Playing..." else message.content,
                                fontFamily = FontFamilySyne,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color(0xB3FFFFFF)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = fileName,
                                fontFamily = FontFamilySyne,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = Color(0x66FFFFFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Message.TYPE_SYSTEM -> {
                        Text(
                            text = message.content,
                            fontFamily = FontFamilySyne,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color(0x66FFFFFF)
                        )
                    }
                }
            }
        }

        Text(
            text = formatTime(message.timestamp),
            fontFamily = FontFamilySyne,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = Color(0x40FFFFFF),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ImageThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path, opts)
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    val bmp = bitmap
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0DFFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!failed) {
            Text(
                text = "Loading...",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0x4DFFFFFF),
                modifier = Modifier.padding(24.dp)
            )
        } else {
            Text(
                text = "Could not load preview",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0x40FFFFFF),
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun VideoThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var frame by remember(path) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val retriever = MediaMetadataRetriever()
        try {
            frame = withContext(Dispatchers.IO) {
                retriever.setDataSource(path)
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
            .background(Color(0x0DFFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Video",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
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
                text = "Loading...",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0x4DFFFFFF),
                modifier = Modifier.padding(24.dp)
            )
        } else {
            Text(
                text = "Could not load preview",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = Color(0x40FFFFFF),
                modifier = Modifier.padding(24.dp)
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
            .background(Color(0x0DFFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0x1AFFFFFF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fileName.substringAfterLast('.', "?").take(3).uppercase(),
                    fontFamily = FontFamilySyne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0x73FFFFFF)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = fileName,
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Tap to open",
                fontFamily = FontFamilySyne,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color(0x40FFFFFF)
            )
        }
    }
}

private fun openFile(context: android.content.Context, path: String, mimeType: String) {
    try {
        val file = java.io.File(path)
        if (!file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
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
        else -> "application/octet-stream"
    }
}

@Composable
private fun NoiseOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val random = Random(42)
        for (i in 0 until 300) {
            drawCircle(
                Color.White,
                radius = 1.0f,
                center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                alpha = 0.018f
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
