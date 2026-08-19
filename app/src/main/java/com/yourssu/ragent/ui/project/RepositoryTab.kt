package com.yourssu.ragent.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project

@Composable
fun RepositoryTab(project: Project) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(project.githubUrl.ifBlank { "GitHub Repository 미연결" }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("디렉터리 / 파일 탐색 UI Mock", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
