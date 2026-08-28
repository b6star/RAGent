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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
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
    val listState = rememberLazyListState()
    
    val apiState = remember { viewModel.aiApiKeyStorage.getState() }

    var inputAreaHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    LaunchedEffect(messages.size, viewModel.isLoading) {
        if (messages.isNotEmpty() || viewModel.isLoading) {
            listState.animateScrollToItem(0)
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
                    availableModels = apiState.provider.models,
                    onBack = onBack,
                    onModelSelected = { viewModel.updateSelectedModel(it) }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .imePadding()
            ) {
                val reversedMessages = messages.asReversed()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = inputAreaHeight + 8.dp,
                        start = 0.dp,
                        end = 0.dp
                    )
                ) {
                    // 에러와 로딩바는 리스트 최하단(역순이므로 가장 먼저 정의)에 배치
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

                    itemsIndexed(
                        items = reversedMessages,
                        key = { _, message -> message.id }
                    ) { _, message ->
                        AiChatBubble(message)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding())
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
    modifier: Modifier = Modifier
) {
    val colors = AgentTheme.colors
    var showModelMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (colors.isDark) {
            colors.background.copy(alpha = 0.9f)
        } else {
            Color(0xFFF1F5F9).copy(alpha = 0.85f)
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = if (colors.isDark) {
                SolidColor(colors.glassBorder)
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.9f),
                        Color(0xFFCBD5E1).copy(alpha = 0.4f)
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
                    DropdownMenu(
                        expanded = showModelMenu,
                        onDismissRequest = { showModelMenu = false },
                        modifier = Modifier.background(colors.background)
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = model.name,
                                            color = colors.onBackground,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (selectedModelId == model.id) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(16.dp).padding(start = 8.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onModelSelected(model.id)
                                    showModelMenu = false
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun AiChatBubble(message: AiChatMessage) {
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
                        Text(
                            text = message.text,
                            color = colors.userText,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
                    onModelSelected = {}
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
