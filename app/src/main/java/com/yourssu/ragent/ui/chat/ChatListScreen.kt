package com.yourssu.ragent.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker
import com.yourssu.ragent.ui.layout.ScreenPadding

@Composable
fun ChatListScreen(
    title: String,
    messages: List<ChatMessage>,
    currentUserId: String,
    personName: (String) -> String,
    personRole: (String) -> Role,
    projectName: (String?) -> String?,
    scrollIndex: Int,
    scrollOffset: Int,
    onScrollPositionChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onNameClick: (String) -> Unit,
    onMessageClick: (ChatMessage) -> Unit
) {
    BackHandler(onBack = onBack)

    var showSent by remember { mutableStateOf(false) }
    val visibleMessages = messages.filter { (it.senderId == currentUserId) == showSent }
    val reversedMessages = visibleMessages.reversed()
    val listState = rememberLazyListState()

    LaunchedEffect(title, showSent, reversedMessages.size) {
        if (reversedMessages.isNotEmpty()) {
            listState.scrollToItem(scrollIndex.coerceAtMost(reversedMessages.lastIndex), scrollOffset)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollPositionChange(index, offset) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChatListHeader(title, if (showSent) "보낸 메시지" else "받은 메시지", showSent, { showSent = it }, onBack)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reversedMessages, key = { it.id }) { message ->
                    MessageListRow(
                        message = message,
                        showSent = showSent,
                        personName = personName,
                        personRole = personRole,
                        projectName = projectName,
                        onNameClick = {
                            onNameClick(if (showSent) message.receiverId else message.senderId)
                        },
                        onClick = { onMessageClick(message) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatListHeader(
    title: String,
    subtitle: String,
    showSent: Boolean,
    onToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
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
        MessageToggle(showSent, onToggle)
    }
}

@Composable
private fun MessageToggle(showSent: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleItem("받은", selected = !showSent, onClick = { onToggle(false) })
            ToggleItem("보낸", selected = showSent, onClick = { onToggle(true) })
        }
    }
}

@Composable
private fun ToggleItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
        )
    }
}

@Composable
private fun MessageListRow(
    message: ChatMessage,
    showSent: Boolean,
    personName: (String) -> String,
    personRole: (String) -> Role,
    projectName: (String?) -> String?,
    onNameClick: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 18.dp)

    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = projectName(message.projectId) ?: "Personal",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                message.createdAt.toDisplayTime(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showSent) "to " else "from ", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Light, fontSize = 15.sp)
                val displayPersonId = if (showSent) message.receiverId else message.senderId
                RoleMarker(personRole(displayPersonId))
                Surface(onClick = onNameClick, color = Color.Transparent) {
                    Text(
                        personName(displayPersonId),
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(message.text, maxLines = 2, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)

        }
    }
}

private fun Long.toDisplayTime(): String {
    val minutes = (this / 60_000L) % 60
    val hours = (this / 3_600_000L) % 24
    return "%02d:%02d".format(hours, minutes)
}
