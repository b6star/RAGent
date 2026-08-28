package com.yourssu.ragent.ui.projectlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker
import com.yourssu.ragent.ui.theme.ConnectedColorDark
import com.yourssu.ragent.ui.theme.ConnectedColorLight
import com.yourssu.ragent.ui.theme.DisconnectedColorDark
import com.yourssu.ragent.ui.theme.DisconnectedColorLight
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    projects: List<Project>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onProjectClick: (Project) -> Unit,
    onProfileClick: () -> Unit,
    onChatClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    var showRefreshComplete by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (wasLoading && !isLoading && errorMessage == null) {
            showRefreshComplete = true
            delay(1000)
            showRefreshComplete = false
        }
        wasLoading = isLoading
    }

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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ProjectListHeader(
                        onProfileClick = onProfileClick,
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

            AnimatedVisibility(
                visible = showRefreshComplete,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RAGentIcon(
                            AppIcon.Check,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "새로고침을 완료했습니다.",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
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
    onProfileClick: () -> Unit,
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
            onClick = onProfileClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "내 프로필",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(10.dp))
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoleMarker(project.myRole, shortLabel = true)
            Spacer(Modifier.width(16.dp))
            Text(
                text = project.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionIndicator(project.githubUrl.isNotBlank(), AppIcon.Github)
                Spacer(Modifier.width(4.dp))
                ConnectionIndicator(project.docsUrl.isNotBlank(), AppIcon.Docs)
            }
        }
    }
}

@Composable
private fun ConnectionIndicator(connected: Boolean, icon: AppIcon) {
    val isDark = isSystemInDarkTheme()
    val tint = if (connected) {
        if (isDark) ConnectedColorDark else ConnectedColorLight
    } else {
        if (isDark) DisconnectedColorDark else DisconnectedColorLight
    }
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = tint.copy(alpha = 0.1f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        RAGentIcon(
            icon = icon,
            color = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}
