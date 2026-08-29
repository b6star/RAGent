package com.yourssu.ragent.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AiConversationPickerDialog(
    sessions: List<AiChatSession>,
    onDismiss: () -> Unit,
    onSelect: (AiChatSession) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화 선택") },
        text = {
            if (sessions.isEmpty()) Text("기존 대화가 없습니다.")
            else LazyColumn {
                items(sessions, key = AiChatSession::id) { session ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onSelect(session) }.padding(vertical = 12.dp)
                    ) {
                        Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(session.lastMessage.ifBlank { "메시지 없음" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
