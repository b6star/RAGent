package com.yourssu.ragent.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.AiSessionTokenUsage
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.ui.project.AiChatMessage
import com.yourssu.ragent.ui.project.AiChatSession
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiChatInfoDialog(
    session: AiChatSession?,
    project: Project,
    usage: AiSessionTokenUsage?,
    messages: List<AiChatMessage>,
    onDismiss: () -> Unit
) {
    val tokenUsage = usage?.usage

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("채팅 정보", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoValue("채팅방", session?.title ?: "새 채팅")
                InfoValue("프로젝트", project.name)
                InfoValue("마지막 메시지 업데이트", formatDateTime(session?.updatedAt))
                InfoValue("메시지 수", "${messages.size}개")

                HorizontalDivider()
                DialogSectionTitle("토큰 사용량")
                TokenValue("총 토큰", tokenUsage?.totalTokens ?: 0)
                TokenValue("입력 토큰", tokenUsage?.inputTokens ?: 0)
                TokenValue("출력 토큰", tokenUsage?.outputTokens ?: 0)
                TokenValue("생각 토큰", tokenUsage?.thoughtsTokens ?: 0)
                InfoValue("AI 요청", "${tokenUsage?.requestCount ?: 0}회")
                TokenValue("개인 API", usage?.personalTokens ?: 0)
                TokenValue("개발자 API", usage?.developerTokens ?: 0)

                HorizontalDivider()
                DialogSectionTitle("모델별 사용량")
                if (usage?.models.isNullOrEmpty()) {
                    Text(
                        "아직 기록된 사용량이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    usage?.models.orEmpty().forEach { model ->
                        TokenValue(model.modelName, model.usage.totalTokens)
                    }
                }

                session?.lastMessage
                    ?.takeIf(String::isNotBlank)
                    ?.let { lastMessage ->
                        HorizontalDivider()
                        DialogSectionTitle("마지막 메시지")
                        Text(
                            lastMessage,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun InfoValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TokenValue(label: String, tokens: Long) {
    InfoValue(label, "${formatDialogTokens(tokens)} 토큰")
}

private fun formatDialogTokens(tokens: Long): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(tokens)

private fun formatDateTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "기록 없음"
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
}
