package com.yourssu.ragent.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker
import com.yourssu.ragent.ui.layout.ScreenPadding

@Composable
fun DirectMessageScreen(
    title: String,
    subtitle: String,
    messages: List<ChatMessage>,
    highlightedProjectId: String?,
    currentUserId: String,
    personName: (String) -> String,
    personRole: (String) -> Role,
    projectName: (String?) -> String?,
    isSelfChat: Boolean,
    scrollIndex: Int,
    scrollOffset: Int,
    onScrollPositionChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onNameClick: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()

    LaunchedEffect(title, messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(scrollIndex.coerceAtMost(messages.lastIndex), scrollOffset)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollPositionChange(index, offset) }
    }
    Scaffold(bottomBar = { ChatInput() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DirectMessageHeader(title, subtitle, onBack)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        highlighted = highlightedProjectId == message.projectId,
                        currentUserId = currentUserId,
                        personName = personName,
                        personRole = personRole,
                        projectName = projectName,
                        showSender = !isSelfChat,
                        onNameClick = { onNameClick(message.senderId) }  // personRole = ) }  // 추후 추가
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectMessageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                RAGentIcon(AppIcon.Back, MaterialTheme.colorScheme.onSurface)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    highlighted: Boolean,
    currentUserId: String,
    personName: (String) -> String,
    personRole: (String) -> Role,
    projectName: (String?) -> String?,
    showSender: Boolean,
    onNameClick: () -> Unit
) {
    val isMine = message.senderId == currentUserId
    val align = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = when {
        isMine -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        highlighted -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (!isMine && showSender) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoleMarker(personRole(message.senderId))
                    Text(
                        text = personName(message.senderId),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable(onClick = onNameClick),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            val cornerRadius = 18.dp
            val shape = if (isMine) {
                RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomEnd = 2.dp, bottomStart = cornerRadius)
            } else {
                RoundedCornerShape(topStart = 2.dp, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = cornerRadius)
            }

            Surface(shape = shape, color = bubbleColor) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(message.text, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = if (isMine) Alignment.End else Alignment.Start
                )
            ) {
                projectName(message.projectId)?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Text(message.createdAt.toDisplayTime(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ChatInput() {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("메시지 입력") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )
        Surface(
            onClick = { text = "" },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary
        ) {
            Box(contentAlignment = Alignment.Center) {
                RAGentIcon(AppIcon.ChatList, Color.White)
            }
        }
    }
}

private fun Long.toDisplayTime(): String {
    val minutes = (this / 60_000L) % 60
    val hours = (this / 3_600_000L) % 24
    return "%02d:%02d".format(hours, minutes)
}
