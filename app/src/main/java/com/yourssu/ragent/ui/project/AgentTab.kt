package com.yourssu.ragent.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.ui.agent.theme.AgentChatTheme
import com.yourssu.ragent.ui.agent.theme.AgentTheme
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgentTab(
    project: Project,
    viewModel: AgentViewModel = viewModel(),
    onSessionClick: (AiChatSession) -> Unit
) {
    val sessions = viewModel.sessions.filter { it.projectId == project.id }

    var sessionToRename by remember { mutableStateOf<AiChatSession?>(null) }
    
    // 탭 진입 시 세션 목록 로드
    LaunchedEffect(project.id) {
        viewModel.loadSessions(project.id)
    }

    AgentChatTheme {
        val colors = AgentTheme.colors
        Box(modifier = Modifier.fillMaxSize()) {
            if (sessions.isEmpty()) {
                EmptySessionsView(onStartChat = { 
                    viewModel.startNewSession(project.id, "새로운 대화", onCreated = onSessionClick)
                })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions) { session ->
                        SessionItem(
                            session = session, 
                            onClick = { onSessionClick(session) },
                            onRename = { sessionToRename = session },
                            onDelete = { viewModel.deleteSession(project.id, session.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }

            viewModel.error?.let {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, start = 16.dp, end = 16.dp),
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer
                ) {
                    Text(it)
                }
            }

            FloatingActionButton(
                onClick = { 
                    viewModel.startNewSession(project.id, "새로운 대화", onCreated = onSessionClick)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    }

    sessionToRename?.let { session ->
        RenameSessionDialog(
            initialTitle = session.title,
            onDismiss = { sessionToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameSession(project.id, session.id, newTitle)
                sessionToRename = null
            }
        )
    }
}

@Composable
fun SessionItem(
    session: AiChatSession, 
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AgentTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = colors.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RAGentIcon(AppIcon.Agent, colors.primary, modifier = Modifier.size(20.dp))
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (session.lastMessage.isNotEmpty()) {
                    Text(
                        text = session.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert, 
                            contentDescription = "Options",
                            tint = colors.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("이름 변경") },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제", color = colors.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
                Text(
                    text = formatTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun RenameSessionDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화 이름 변경") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun EmptySessionsView(onStartChat: () -> Unit) {
    val colors = AgentTheme.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RAGentIcon(AppIcon.Agent, colors.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "시작된 대화가 없습니다.",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStartChat,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            )
        ) {
            Text("새로운 대화 시작하기")
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
