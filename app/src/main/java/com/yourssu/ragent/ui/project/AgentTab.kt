package com.yourssu.ragent.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AgentTab() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("프로젝트 Agent", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("현재 프로젝트에 질문하는 채팅 UI 자리입니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("RAG와 Local LLM은 이후 단계에서 연결합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
