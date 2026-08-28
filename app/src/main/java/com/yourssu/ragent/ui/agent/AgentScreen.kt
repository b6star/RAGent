package com.yourssu.ragent.ui.agent

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import java.util.UUID
import com.yourssu.ragent.model.AiModel
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.ui.agent.theme.AgentChatTheme
import com.yourssu.ragent.ui.agent.theme.AgentTheme
import com.yourssu.ragent.ui.agent.theme.AgentThemeType
import com.yourssu.ragent.ui.agent.theme.AgentColors
import com.yourssu.ragent.ui.project.AgentViewModel
import com.yourssu.ragent.ui.project.AiChatMessage
import com.yourssu.ragent.ui.project.AiChatSession
import com.yourssu.ragent.ui.project.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    project: Project,
    viewModel: AgentViewModel,
    onBack: () -> Unit
) {
    val sessionId = viewModel.currentSessionId ?: return
    val messages = viewModel.getMessagesForSession(sessionId)
    val session = viewModel.sessions.find { it.id == sessionId }
    val sessionUsage = viewModel.usageDashboard.sessionUsages[sessionId]
    val listState = rememberLazyListState()
    var showChatInfo by remember(sessionId) { mutableStateOf(false) }
    
    val apiState = remember { viewModel.aiApiKeyStorage.getState() }

    var inputAreaHeight by remember { mutableStateOf(0.dp) }
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
                    ChatInputArea(
                        onSend = { viewModel.askQuestion(project.id, it) },
                        isLoading = viewModel.isLoading
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
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
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
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = if (isUser) 8.dp else 10.dp)) {
                    if (isUser) {
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
                    .padding(horizontal = 20.dp, vertical = 14.dp)
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
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
