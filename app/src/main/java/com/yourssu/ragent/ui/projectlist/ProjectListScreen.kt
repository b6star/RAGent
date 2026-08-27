package com.yourssu.ragent.ui.projectlist

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker
import com.yourssu.ragent.ui.theme.ConnectedColorDark
import com.yourssu.ragent.ui.theme.ConnectedColorLight
import com.yourssu.ragent.ui.theme.DisconnectedColorDark
import com.yourssu.ragent.ui.theme.DisconnectedColorLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    projects: List<Project>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onProjectClick: (Project) -> Unit,
    onChatClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Scaffold { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRetry,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ProjectListHeader(
                        onChatClick = onChatClick,
                        onCreateClick = onCreateClick
                    )
                }
                when {
                    errorMessage != null -> item {
                        ProjectListStatus(errorMessage, actionLabel = "다시 시도", onAction = onRetry)
                    }
                    isLoading -> Unit
                    projects.isEmpty() -> item {
                        ProjectListStatus("참여 중인 프로젝트가 없습니다.")
                    }
                }
                items(projects) { project ->
                    ProjectCard(project = project, onClick = { onProjectClick(project) })
                }
            }
        }
    }
}

@Composable
private fun ProjectListStatus(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ProjectListHeader(
    onChatClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RAGent",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
        Surface(
            onClick = onChatClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                RAGentIcon(AppIcon.ChatList, MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            onClick = onCreateClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                RAGentIcon(AppIcon.Plus, MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoleMarker(project.myRole, shortLabel = true)
            Spacer(Modifier.width(12.dp))
            Text(
                text = project.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ConnectionIcon(project.githubUrl.isNotBlank(), description = "GitHub 연결", AppIcon.Github)
            ConnectionIcon(project.docsUrl.isNotBlank(), description = "Docs 연결", AppIcon.Docs)
        }
    }
}

@Composable
private fun ConnectionIcon(connected: Boolean, description: String, icon: AppIcon) {
    val isDark = isSystemInDarkTheme()
    IconButton(
        onClick = {},
        modifier = Modifier
            .size(42.dp)
            .semantics { contentDescription = description }
    ) {
        RAGentIcon(
            icon,
            if (connected) {
                if (isDark) ConnectedColorDark else ConnectedColorLight
            } else {
                if (isDark) DisconnectedColorDark else DisconnectedColorLight
            }
        )
    }
}
