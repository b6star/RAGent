package com.yourssu.ragent.ui.agent

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import coil3.compose.AsyncImage
import com.yourssu.ragent.model.AiModel
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.ui.agent.theme.AgentChatTheme
import com.yourssu.ragent.ui.agent.theme.AgentTheme
import com.yourssu.ragent.ui.agent.theme.AgentThemeType
import com.yourssu.ragent.ui.agent.theme.AgentColors
import com.yourssu.ragent.ui.project.AgentViewModel
import com.yourssu.ragent.ui.project.AiChatMessage
import com.yourssu.ragent.ui.project.AiChatSession
import com.yourssu.ragent.ui.project.AiSelectionDraft
import com.yourssu.ragent.ui.project.AiSelectionKind
import com.yourssu.ragent.ui.project.AiAttachment
import com.yourssu.ragent.ui.project.formatTime

private const val MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L
private const val MAX_TOTAL_ATTACHMENT_BYTES = 20L * 1024L * 1024L

private fun Uri.toAttachment(context: Context): AiAttachment {
    val displayName = lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "image"
    return AiAttachment(
        uri = toString(),
        mimeType = context.contentResolver.getType(this) ?: "application/octet-stream",
        displayName = displayName,
        sizeBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(this, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: 0L
        ,dataBase64 = runCatching {
            context.contentResolver.openInputStream(this)?.use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        }.getOrNull()
    )
}

private fun Bitmap.toAttachment(context: Context): AiAttachment {
    val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output ->
        compress(Bitmap.CompressFormat.JPEG, 90, output)
    }
    return AiAttachment(
        uri = Uri.fromFile(file).toString(),
        mimeType = "image/jpeg",
        displayName = file.name,
        sizeBytes = file.length(),
        dataBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    project: Project,
    viewModel: AgentViewModel,
    initialSelection: AiSelectionDraft? = null,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val sessionId = viewModel.currentSessionId ?: return
    val messages = viewModel.getMessagesForSession(sessionId)
    var routeSelection by remember(sessionId, initialSelection) {
        mutableStateOf(initialSelection)
    }
    val pendingSelection = routeSelection ?: viewModel.pendingSelection
        ?.takeIf { it.projectId == project.id }
    val session = viewModel.sessions.find { it.id == sessionId }
    val sessionUsage = viewModel.usageDashboard.sessionUsages[sessionId]
    val listState = rememberLazyListState()
    var showChatInfo by remember(sessionId) { mutableStateOf(false) }
    
    val apiState = remember { viewModel.aiApiKeyStorage.getState() }

    var inputAreaHeight by remember { mutableStateOf(0.dp) }
    var attachments by remember(sessionId, initialSelection) {
        mutableStateOf(
            initialSelection
                ?.takeIf { it.kind == AiSelectionKind.Image }
                ?.sourceSelection?.capturedImage
                ?.let { listOf(it) }
                ?: emptyList()
        )
    }
    var attachmentError by remember(sessionId) { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun addAttachment(attachment: AiAttachment) {
        when {
            attachment.sizeBytes > MAX_ATTACHMENT_BYTES -> {
                attachmentError = "파일 하나당 최대 10MB까지 첨부할 수 있습니다."
            }
            attachments.sumOf { it.sizeBytes } + attachment.sizeBytes > MAX_TOTAL_ATTACHMENT_BYTES -> {
                attachmentError = "한 번에 최대 20MB까지 첨부할 수 있습니다."
            }
            else -> {
                attachmentError = null
                attachments = attachments + attachment
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri -> addAttachment(uri.toAttachment(context)) }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { addAttachment(it.toAttachment(context)) }
    }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { addAttachment(it.toAttachment(context)) }
    }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var previousImeBottom by remember(sessionId) { mutableIntStateOf(imeBottom) }

    val lastContentIndex = messages.size +
        (if (viewModel.isLoading) 1 else 0) +
        (if (viewModel.error != null) 1 else 0) - 1

    // IME가 움직인 거리만큼 현재 리스트 위치를 같은 방향으로 이동한다.
    // 특정 메시지로 강제 이동하지 않아 사용자가 보고 있던 위치를 유지한다.
    SideEffect {
        val imeDelta = imeBottom - previousImeBottom
        if (imeDelta != 0) {
            listState.dispatchRawDelta(imeDelta.toFloat())
        }
        previousImeBottom = imeBottom
    }

    LaunchedEffect(messages.size, viewModel.isLoading, viewModel.error) {
        if (lastContentIndex >= 0) {
            listState.scrollToItem(lastContentIndex)
        }
    }

    AgentChatTheme {
        val colors = AgentTheme.colors
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                AgentChatHeader(
                    session = session,
                    project = project,
                    selectedModelId = viewModel.selectedModelId,
                    availableModels = if (apiState.hasStoredKey) {
                        apiState.provider.models
                    } else {
                        apiState.provider.models.take(1)
                    },
                    onBack = onBack,
                    onModelSelected = { viewModel.updateSelectedModel(it) },
                    onInfoClick = { showChatInfo = true }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = inputAreaHeight + 8.dp,
                        start = 0.dp,
                        end = 0.dp
                    )
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, message -> message.id }
                    ) { index, message ->
                        AiChatBubble(
                            message = message,
                            isLast = index == messages.size - 1
                        )
                    }

                    if (viewModel.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                AiLoadingIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    viewModel.error?.let {
                        item {
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                color = colors.errorContainer.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = it,
                                    color = colors.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Transparent)
                        .onGloballyPositioned { coordinates ->
                            inputAreaHeight = with(density) { coordinates.size.height.toDp() }
                        }
                ) {
                    SelectionChatInputArea(
                        isLoading = viewModel.isLoading,
                        attachedSelection = pendingSelection,
                        onRemoveSelection = {
                            routeSelection = null
                            viewModel.clearPendingSelection()
                        },
                        attachments = attachments,
                        onPickImage = { imagePicker.launch("image/*") },
                        onPickFile = { filePicker.launch(arrayOf("*/*")) },
                        onTakePhoto = { cameraPicker.launch(null) },
                        onRemoveAttachment = { attachment ->
                            attachments = attachments.filterNot { it.uri == attachment.uri }
                        },
                        attachmentError = attachmentError,
                        onSendWithAttachments = { text, files ->
                            viewModel.askQuestion(project.id, text, files)
                            routeSelection = null
                            attachments = emptyList()
                        }
                    )
                }
            }
        }

        if (showChatInfo) {
            AiChatInfoDialog(
                session = session,
                project = project,
                usage = sessionUsage,
                messages = messages,
                onDismiss = { showChatInfo = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatHeader(
    session: AiChatSession?,
    project: Project,
    selectedModelId: String?,
    availableModels: List<AiModel>,
    onBack: () -> Unit,
    onModelSelected: (String) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AgentTheme.colors
    var showModelMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (colors.isDark) {
            colors.background.copy(alpha = 0.88f)
        } else {
            Color(0xFFF1F5F9).copy(alpha = 0.88f)
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            brush = if (colors.isDark) {
                SolidColor(colors.glassBorder)
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color(0xFFCBD5E1).copy(alpha = 0.3f)
                    )
                )
            }
        ),
        shadowElevation = if (colors.isDark) 0.dp else 10.dp
    ) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = session?.title ?: "New Chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onBackground
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showModelMenu = true }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Select Model",
                            tint = colors.onBackground
                        )
                    }
                    ModelSelectionMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        selectedModelId = selectedModelId,
                        availableModels = availableModels,
                        onModelSelected = onModelSelected
                    )
                }
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "채팅 정보",
                        tint = colors.onBackground
                    )
                }
            }
        )
    }
}

@Composable
fun ModelSelectionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedModelId: String?,
    availableModels: List<AiModel>,
    onModelSelected: (String) -> Unit
) {
    val colors = AgentTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(
                if (colors.isDark) colors.surface.copy(alpha = 0.95f)
                else Color.White.copy(alpha = 0.95f)
            )
            .widthIn(min = 180.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        val geminiModels = availableModels.filter {
            it.name.lowercase().contains("gemini") || it.id.lowercase().contains("gemini")
        }
        val gptModels = availableModels.filter {
            it.name.lowercase().contains("gpt") || it.id.lowercase().contains("gpt")
        }
        val otherModels = availableModels.filter { model ->
            geminiModels.none { it.id == model.id } && gptModels.none { it.id == model.id }
        }

        // Gemini Group
        if (geminiModels.isNotEmpty()) {
            geminiModels.forEach { model ->
                ModelMenuItem(model, selectedModelId, colors, onModelSelected, onDismissRequest)
            }
        }

        // Divider between Gemini and others
        if (geminiModels.isNotEmpty() && (gptModels.isNotEmpty() || otherModels.isNotEmpty())) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                thickness = 0.5.dp,
                color = colors.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }

        // GPT Group
        if (gptModels.isNotEmpty()) {
            gptModels.forEach { model ->
                ModelMenuItem(model, selectedModelId, colors, onModelSelected, onDismissRequest)
            }
        }

        // Divider between GPT and others
        if (gptModels.isNotEmpty() && otherModels.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                thickness = 0.5.dp,
                color = colors.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }

        // Other models
        if (otherModels.isNotEmpty()) {
            otherModels.forEach { model ->
                ModelMenuItem(model, selectedModelId, colors, onModelSelected, onDismissRequest)
            }
        }
    }
}

@Composable
private fun ModelMenuItem(
    model: AiModel,
    selectedModelId: String?,
    colors: AgentColors,
    onModelSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val isSelected = selectedModelId == model.id
    DropdownMenuItem(
        text = {
            Text(
                text = model.name,
                color = if (isSelected) Color.White else colors.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth()
            )
        },
        onClick = {
            onModelSelected(model.id)
            onDismissRequest()
        },
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .background(
                color = if (isSelected) colors.userBubble else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
    )
}

@Composable
fun AiChatBubble(message: AiChatMessage, isLast: Boolean = false) {
    val isUser = message.isUser
    val colors = AgentTheme.colors
    var previewAttachments by remember(message.id) { mutableStateOf<List<AiAttachment>?>(null) }
    val imageAttachments = message.attachments.filter { it.mimeType.startsWith("image/") }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser && imageAttachments.isNotEmpty()) {
            UserImageAttachments(
                attachments = imageAttachments,
                onImageClick = { previewAttachments = it }
            )
        }
        if (isUser && !message.selectedText.isNullOrBlank()) {
            SelectedSourceCaption(message)
        }
        Column(
            modifier = if (isUser) {
                Modifier
                    .widthIn(max = 280.dp)
                    .padding(start = 60.dp, end = 16.dp)
                    .wrapContentWidth(Alignment.End)
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            Surface(
                color = if (isUser) colors.userBubble else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
                modifier = if (isUser) Modifier.wrapContentSize() else Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = if (isUser) 8.dp else 10.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isUser) {
                        message.attachments
                            .filterNot { it.mimeType.startsWith("image/") }
                            .forEach { attachment ->
                                Text(
                                    text = "📎 ${attachment.displayName}",
                                    color = colors.userText.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        SelectionContainer {
                            Text(
                                text = message.text,
                                color = colors.userText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        AiChatMarkdownView(
                            markdown = message.text,
                            isUser = isUser
                        )
                    }
                }
            }
            
            if (!isUser) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AiMetadataView(message)
                }
            } else {
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp, end = 8.dp)
                )
            }
        }

        previewAttachments?.let { attachments ->
            AttachmentPreviewDialog(
                attachments = attachments,
                onDismiss = { previewAttachments = null }
            )
        }
    }
}

@Composable
private fun SelectedSourceCaption(message: AiChatMessage) {
    val uriHandler = LocalUriHandler.current
    val link = message.canonicalUrls.firstOrNull()
    var showFullText by remember(message.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .padding(end = 16.dp),
        color = Color(0xFF1A1A1B),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = message.selectedText!!.replace(Regex("\\s+"), " ").trim(),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { showFullText = true }
            )
            link?.let {
                Text(
                    text = it,
                    color = Color(0xFF8AB4F8),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { uriHandler.openUri(it) }
                )
            }
        }
    }
    if (showFullText) {
        AlertDialog(
            onDismissRequest = { showFullText = false },
            title = { Text("선택한 텍스트") },
            text = { Text(message.selectedText!!.trim()) },
            confirmButton = {
                TextButton(onClick = { showFullText = false }) { Text("닫기") }
            }
        )
    }
}

@Composable
private fun UserImageAttachments(
    attachments: List<AiAttachment>,
    onImageClick: (List<AiAttachment>) -> Unit
) {
    val visibleAttachments = if (attachments.size >= 3) attachments.take(2) else attachments
    Row(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        visibleAttachments.forEachIndexed { index, attachment ->
            Box(
                modifier = Modifier
                    .size(if (attachments.size >= 3) 128.dp else 104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImageClick(attachments) }
            ) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = attachment.displayName,
                    modifier = Modifier.fillMaxSize()
                )
                if (attachments.size >= 3 && index == 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${attachments.size - 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun AttachmentPreviewDialog(
    attachments: List<AiAttachment>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            color = Color.Black.copy(alpha = 0.96f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(attachments, key = { it.uri }) { attachment ->
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = attachment.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "미리보기 닫기",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AiMetadataView(message: AiChatMessage) {
    val colors = AgentTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val metadataColor = colors.metadataText.copy(alpha = 0.7f)
            Text(
                text = message.modelName ?: "UNKNOWN",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = metadataColor
            )
            if (message.totalTokens != null) {
                Text(
                    text = "•  ${message.totalTokens} tokens",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = metadataColor
                )
            }
            if (message.responseTimeMs != null) {
                Text(
                    text = "•  ${message.responseTimeMs / 1000.0}s",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = metadataColor
                )
            }
        }
        Text(
            text = formatTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.metadataText.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ChatInputArea(onSend: (String) -> Unit, isLoading: Boolean, modifier: Modifier = Modifier) {
    var inputText by remember { mutableStateOf("") }
    val colors = AgentTheme.colors
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 입력창 디자인
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp, max = 150.dp),
            color = colors.glassBackground,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.3f)
                    )
                )
            ),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Ask anything...",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        enabled = !isLoading
                )
            }
        }

        // 별도의 원형 전송 버튼
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(enabled = !isLoading && inputText.isNotBlank()) {
                    onSend(inputText)
                    inputText = ""
                },
            color = colors.glassBackground,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.5f)
                    )
                )
            ),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp),
                    tint = if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun SelectionCaption(
    selection: AiSelectionDraft?,
    onRemoveSelection: () -> Unit
) {
    selection ?: return
    val normalizedText = selection.sourceSelection
        ?.selectedText
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
    val caption = when {
        selection.kind == AiSelectionKind.Image -> "선택한 이미지"
        normalizedText.isBlank() -> "선택한 텍스트"
        normalizedText.length > 80 -> normalizedText.take(77) + "..."
        else -> normalizedText
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // 캡션은 시스템 테마와 관계없이 Agent 다크 UI와 동일한 색상을 사용한다.
        color = Color(0xFF1A1A1B),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.6.dp,
            color = Color.White.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = caption,
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 2
            )
            IconButton(
                onClick = onRemoveSelection,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "선택 항목 제거",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SelectionChatInputArea(
    isLoading: Boolean,
    attachedSelection: AiSelectionDraft?,
    onRemoveSelection: () -> Unit,
    attachments: List<AiAttachment>,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveAttachment: (AiAttachment) -> Unit,
    attachmentError: String? = null,
    onSendWithAttachments: (String, List<AiAttachment>) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val colors = AgentTheme.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectionCaption(
            selection = attachedSelection,
            onRemoveSelection = onRemoveSelection
        )

        attachmentError?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        modifier = Modifier.size(width = 76.dp, height = 76.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box {
                            if (attachment.mimeType.startsWith("image/")) {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = attachment.displayName,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = attachment.displayName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 3
                                )
                            }
                            IconButton(
                                onClick = { onRemoveAttachment(attachment) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "첨부 파일 제거",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isLoading) {
                        showAttachmentMenu = true
                    },
                color = colors.glassBackground,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.5f)
                        )
                    )
                ),
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "첨부",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
            DropdownMenu(
                expanded = showAttachmentMenu,
                onDismissRequest = { showAttachmentMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("이미지") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    onClick = { showAttachmentMenu = false; onPickImage() }
                )
                DropdownMenuItem(
                    text = { Text("파일") },
                    leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                    onClick = { showAttachmentMenu = false; onPickFile() }
                )
                DropdownMenuItem(
                    text = { Text("카메라") },
                    leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    onClick = { showAttachmentMenu = false; onTakePhoto() }
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            color = colors.glassBackground,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 0.7.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.1f),
                        Color.White.copy(alpha = 0.3f)
                    )
                )
            ),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (attachedSelection == null) {
                                "Ask anything..."
                            } else {
                                "선택한 내용에 대해 질문해 보세요"
                            },
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        maxLines = 3,
                        enabled = !isLoading
                    )
                }
            }
        }

            Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(enabled = !isLoading && inputText.isNotBlank()) {
                    onSendWithAttachments(inputText, attachments)
                    inputText = ""
                },
            color = colors.glassBackground,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.5f)
                    )
                )
            ),
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(24.dp),
                    tint = if (inputText.isNotBlank()) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.3f)
                    }
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun AgentChatScreenPreview() {
    val mockSessionId = UUID.randomUUID().toString()
    val mockMessages = listOf(
        AiChatMessage(sessionId = mockSessionId, text = "Hello! How can I help you today?", isUser = false, modelName = "gemini-3.5-flash-lite"),
        AiChatMessage(sessionId = mockSessionId, text = "test?", isUser = true),
        AiChatMessage(sessionId = mockSessionId, text = "Sure! Kotlin is a modern programming language.", isUser = false, modelName = "gemini-3.5-flash-lite")
    )
    
    AgentChatTheme(themeType = AgentThemeType.DEFAULT, isDark = true) {
        val colors = AgentTheme.colors
        Scaffold(
            containerColor = colors.background,
            topBar = {
                AgentChatHeader(
                    session = AiChatSession(title = "Kotlin Support", projectId = "1"),
                    project = Project(id = "1", name = "RAGent Project", myRole = com.yourssu.ragent.model.Role.Admin),
                    selectedModelId = "gemini-3.5-flash-lite",
                    availableModels = listOf(AiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash")),
                    onBack = {},
                    onModelSelected = {},
                    onInfoClick = {}
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = 100.dp
                    )
                ) {
                    items(mockMessages) { message ->
                        AiChatBubble(message)
                    }
                }

                ChatInputArea(
                    onSend = {},
                    isLoading = false,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
