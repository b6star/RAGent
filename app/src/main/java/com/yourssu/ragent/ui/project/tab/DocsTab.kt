package com.yourssu.ragent.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.PublicSourceUrl
import com.yourssu.ragent.ui.agent.theme.AgentTheme

@Composable
fun DocsTab(project: Project, onExit: () -> Unit, visible: Boolean = true, selectionRequest: SourceSelectionRequest? = null, onSelectionResolved: (SourceSelectionResult) -> Unit = {}, onSelectionImageCaptured: (AiAttachment) -> Unit = {}) {
    SourceWebView(project.docsUrl, "Notion 문서 연결 없음", onExit, visible, applyNotionScrollFix = true, darkTheme = isSystemInDarkTheme(), stateKey = "${project.id}:notion", selectionRequest = selectionRequest, onSelectionResolved = onSelectionResolved)
    return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AgentTheme.colors.background),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(PublicSourceUrl.notionCaption(project.docsUrl), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Docs는 Firebase 서버가 아니라 프로젝트 문서 연결 영역입니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
