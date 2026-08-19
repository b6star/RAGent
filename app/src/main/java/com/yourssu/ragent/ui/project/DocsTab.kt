package com.yourssu.ragent.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DocsTab() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Notion 문서 Mock", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Docs는 Firebase 서버가 아니라 프로젝트 문서 연결 영역입니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
