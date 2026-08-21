package com.yourssu.ragent.ui.chat

import androidx.compose.runtime.Composable
import com.yourssu.ragent.mock.CurrentUserId
import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.navigation.AppScreen
import com.yourssu.ragent.ui.navigation.ScrollPosition

@Composable
fun ChatRoute(
    chat: AppScreen.Chat,
    messages: List<ChatMessage>,
    personName: (String) -> String,
    personRole: (String) -> Role,
    projectName: (String?) -> String?,
    listScrollPosition: ScrollPosition,
    directScrollPosition: ScrollPosition,
    onListScrollChange: (Int, Int) -> Unit,
    onDirectScrollChange: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onPersonClick: (String) -> Unit,
    onMessageClick: (ChatMessage) -> Unit
) {
    if (chat.listMode) {
        ChatListScreen(
            title = chat.title,
            messages = messages,
            currentUserId = CurrentUserId,
            personName = personName,
            personRole = personRole,
            projectName = projectName,
            scrollIndex = listScrollPosition.index,
            scrollOffset = listScrollPosition.offset,
            onScrollPositionChange = onListScrollChange,
            onBack = onBack,
            onNameClick = onPersonClick,
            onMessageClick = onMessageClick
        )
    } else {
        DirectMessageScreen(
            title = chat.title,
            subtitle = chat.subtitle,
            messages = messages,
            highlightedProjectId = chat.project?.id,
            currentUserId = CurrentUserId,
            personName = personName,
            personRole = personRole,
            projectName = projectName,
            isSelfChat = chat.member?.personId == CurrentUserId,
            scrollIndex = directScrollPosition.index,
            scrollOffset = directScrollPosition.offset,
            onScrollPositionChange = onDirectScrollChange,
            onBack = onBack,
            onNameClick = onPersonClick
        )
    }
}
