package com.yourssu.ragent.ui.project

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Rect
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectTab
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.model.SourceSyncStatusDocument
import com.yourssu.ragent.model.RagRevisionStatusDocument
import com.yourssu.ragent.ui.agent.theme.AgentTheme
import com.yourssu.ragent.ui.agent.theme.AgentThemeType
import com.yourssu.ragent.ui.agent.theme.AgentChatTheme
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHomeScreen(
    project: Project,
    selectedTab: ProjectTab,
    isLoading: Boolean,
    errorMessage: String?,
    sourceSyncStatus: SourceSyncStatusDocument?,
    ragRevisionStatus: RagRevisionStatusDocument?,
    onRefresh: () -> Unit,
    onTabSelected: (ProjectTab) -> Unit,
    onBack: () -> Unit,
    personName: (String) -> String,
    membersScrollIndex: Int,
    membersScrollOffset: Int,
    onMembersScrollPositionChange: (Int, Int) -> Unit,
    onProjectChatClick: () -> Unit,
    onAiSelectClick: () -> Unit,
    onLoadAiSessions: (String) -> Unit,
    aiSessions: List<AiChatSession>,
    onAiSelectExisting: (Rect, String, AiSelectionKind, SourceSelectionResult?, AiChatSession) -> Unit,
    onAiSelectNew: (Rect, String, AiSelectionKind, SourceSelectionResult?) -> Unit,
    onMemberChatClick: (ProjectMember) -> Unit,
    onMemberClick: (ProjectMember) -> Unit,
    onMemberRoleChange: (ProjectMember, Role) -> Unit,
    onMemberDelete: (ProjectMember) -> Unit,
    onCreateInvite: (Role, Boolean) -> Unit,
    onSourceLinksChange: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onProjectVisibilityChange: (ProjectVisibility, (Boolean) -> Unit) -> Unit,
    onDeleteProject: () -> Unit,
    onLeaveProject: () -> Unit,
    onAgentSessionClick: (AiChatSession) -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    var showRefreshComplete by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }
    var aiSelectMode by remember { mutableStateOf(false) }
    var pendingExistingSelection by remember { mutableStateOf<Triple<Rect, String, AiSelectionKind>?>(null) }
    var sourceSelectionRequest by remember { mutableStateOf<SourceSelectionRequest?>(null) }
    var resolvedSourceSelection by remember { mutableStateOf<SourceSelectionResult?>(null) }

    LaunchedEffect(project.id) {
        onLoadAiSessions(project.id)
    }

    LaunchedEffect(isLoading) {
        if (wasLoading && !isLoading && errorMessage == null) {
            showRefreshComplete = true
            delay(1000)
            showRefreshComplete = false
        }
        wasLoading = isLoading
    }

    BackHandler(
        enabled = aiSelectMode,
        onBack = {
            aiSelectMode = false
            sourceSelectionRequest = null
            resolvedSourceSelection = null
        }
    )

    BackHandler(
        enabled = selectedTab != ProjectTab.Docs && selectedTab != ProjectTab.Repository,
        onBack = onBack
    )

    AgentChatTheme(themeType = AgentThemeType.DEFAULT) {
        val colors = AgentTheme.colors
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                ProjectHeader(
                    projectName = project.name,
                    horizontalPadding = 16.dp,
                    onBack = onBack,
                    onDetailsClick = { showDetails = true },
                    onChatClick = onProjectChatClick,
                    onAiSelectClick = { aiSelectMode = !aiSelectMode; onAiSelectClick() }
                )
            }
        ) { padding ->
            val topPadding = padding.calculateTopPadding()
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
            ) {
                // 본문 (Content) Layer
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding)
                    ) {
                        SourceSyncStatusBanner(
                            status = sourceSyncStatus
                                ?: SourceSyncStatusDocument(status = "checking"),
                            ragRevisionStatus = ragRevisionStatus,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(2f)
                                .padding(top = 8.dp)
                        )
                        DocsTab(
                            project,
                            onBack,
                            visible = selectedTab == ProjectTab.Docs,
                            selectionRequest = if (selectedTab == ProjectTab.Docs) sourceSelectionRequest else null,
                            onSelectionResolved = {
                                resolvedSourceSelection = it
                                Log.d("SourceWebView", "ProjectHome received selection: $it")
                            }
                        )
                        RepositoryTab(
                            project,
                            onBack,
                            visible = selectedTab == ProjectTab.Repository,
                            selectionRequest = if (selectedTab == ProjectTab.Repository) sourceSelectionRequest else null,
                            onSelectionResolved = {
                                resolvedSourceSelection = it
                                Log.d("SourceWebView", "ProjectHome received selection: $it")
                            }
                        )
                        when (selectedTab) {
                            ProjectTab.Docs, ProjectTab.Repository -> Unit
                            ProjectTab.Members -> MembersTab(
                                members = project.members,
                                personName = personName,
                                canManageMembers = project.myRole == Role.Admin,
                                scrollIndex = membersScrollIndex,
                                scrollOffset = membersScrollOffset,
                                onScrollPositionChange = onMembersScrollPositionChange,
                                onMemberChatClick = onMemberChatClick,
                                onMemberClick = onMemberClick,
                                onRoleChange = onMemberRoleChange,
                                onMemberDelete = onMemberDelete
                            )
                            ProjectTab.Agent -> AgentTab(project = project, onSessionClick = onAgentSessionClick)
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

                // 하단 바
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ProjectBottomBar(selectedTab, onTabSelected)
                }

            }
        }

        // Overlay must be a sibling above Scaffold, otherwise the Scaffold
        // topBar always paints over controls near the top of the content.
        if (aiSelectMode && (selectedTab == ProjectTab.Docs || selectedTab == ProjectTab.Repository)) {
            AiSelectOverlay(
                onDismiss = { aiSelectMode = false; sourceSelectionRequest = null },
                        onSelectionChanged = { rect ->
                            sourceSelectionRequest = if (rect.isEmpty) {
                                null
                            } else {
                                SourceSelectionRequest(rect.left, rect.top, rect.right, rect.bottom)
                            }
                            if (rect.isEmpty) resolvedSourceSelection = null
                        },
                onAskExisting = { rect, kind ->
                    pendingExistingSelection = Triple(rect, if (selectedTab == ProjectTab.Docs) project.docsUrl else project.githubUrl, kind)
                    aiSelectMode = false
                },
                onAskNew = { rect, kind ->
                    onAiSelectNew(
                        rect,
                        if (selectedTab == ProjectTab.Docs) project.docsUrl else project.githubUrl,
                        kind,
                        resolvedSourceSelection
                    )
                    aiSelectMode = false
                }
            )
        }
    }

    pendingExistingSelection?.let { pending ->
        AiConversationPickerDialog(
            sessions = aiSessions.filter { it.projectId == project.id },
            onDismiss = { pendingExistingSelection = null },
            onSelect = { session ->
                onAiSelectExisting(
                    pending.first,
                    pending.second,
                    pending.third,
                    resolvedSourceSelection,
                    session
                )
                pendingExistingSelection = null
            }
        )
    }

    if (showDetails) {
        ModalBottomSheet(
            onDismissRequest = { showDetails = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ProjectDetailsSheet(
                project = project,
                personName = personName,
                onMemberClick = {
                    showDetails = false
                    onMemberClick(it)
                },
                onCreateInvite = onCreateInvite,
                onSourceLinksChange = onSourceLinksChange,
                onProjectVisibilityChange = onProjectVisibilityChange,
                onDeleteProject = {
                    showDetails = false
                    onDeleteProject()
                },
                onLeaveProject = onLeaveProject
            )
        }
    }
}

@Composable
private fun SourceSyncStatusBanner(
    status: SourceSyncStatusDocument,
    ragRevisionStatus: RagRevisionStatusDocument? = null,
    modifier: Modifier = Modifier
) {
    val embeddingStatus = when (ragRevisionStatus?.status?.lowercase()) {
        "pending", "chunking" -> "Embedding 준비 중 ${embeddingProgress(ragRevisionStatus)}" to Color(0xFF7C3AED)
        "embedding" -> "Embedding 생성 중 ${embeddingProgress(ragRevisionStatus)}" to Color(0xFF7C3AED)
        "failed" -> "Embedding 오류 ${embeddingProgress(ragRevisionStatus)}" to Color(0xFFDC2626)
        "ready" -> "Embedding 최신 상태 ${embeddingProgress(ragRevisionStatus)}" to Color(0xFF16A34A)
        else -> null
    }
    val (label, color) = embeddingStatus ?: when (status.status.lowercase()) {
        "queued", "checking" -> "Source 확인 중" to Color(0xFF2563EB)
        "changed" -> "Source 변경 감지됨" to Color(0xFFD97706)
        "error" -> "Source 동기화 오류" to Color(0xFFDC2626)
        "ready" -> "Source 최신 상태" to Color(0xFF16A34A)
        else -> return
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

private fun embeddingProgress(status: RagRevisionStatusDocument): String {
    val total = status.chunkCount
    if (total <= 0) return ""
    val completed = if (status.status.equals("ready", ignoreCase = true)) {
        total
    } else {
        status.completedChunkCount.coerceIn(0, total)
    }
    return "${completed}/${total}"
}

@Composable
private fun ProjectHeader(
    projectName: String,
    horizontalPadding: Dp,
    onBack: () -> Unit,
    onDetailsClick: () -> Unit,
    onChatClick: () -> Unit,
    onAiSelectClick: () -> Unit
) {
    val colors = AgentTheme.colors
    Surface(
        color = if (colors.isDark) {
            colors.background.copy(alpha = 0.88f)
        } else {
            Color(0xFFF1F5F9).copy(alpha = 0.88f)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            brush = if (colors.isDark) {
                SolidColor(colors.glassBorder)
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color(0xFFCBD5E1).copy(alpha = 0.3f)
                    )
                )
            }
        ),
        shadowElevation = if (colors.isDark) 0.dp else 10.dp
    ) {
        Column {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(onClick = onBack, icon = AppIcon.Back)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .background(Color.Transparent),
                ) {
                    Text(
                        text = projectName,
                        color = colors.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CircleButton(onClick = onAiSelectClick, icon = AppIcon.AiSelect)
                Spacer(Modifier.width(6.dp))
                CircleButton(onClick = onChatClick, icon = AppIcon.ChatList)
                Spacer(Modifier.width(10.dp))
                CircleButton(onClick = onDetailsClick, icon = AppIcon.More)
            }
        }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, icon: AppIcon) {
    Surface(onClick = onClick, modifier = Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) {
            RAGentIcon(icon, MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ProjectBottomBar(selectedTab: ProjectTab, onTabSelected: (ProjectTab) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(modifier = Modifier.widthIn(max = 420.dp), shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ProjectTab.entries.forEach { tab -> BottomBarItem(tab, selectedTab == tab) { onTabSelected(tab) } }
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(tab: ProjectTab, selected: Boolean, onClick: () -> Unit) {
    val colors = AgentTheme.colors
    val iconColor = if (selected) Color.White else colors.onBackground.copy(alpha = 0.6f)
    val backgroundColor = if (selected) Color.Black else Color.Transparent
    
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RAGentIcon(tab.icon, iconColor)
        }
    }
}

private val ProjectTab.icon: AppIcon
    get() = when (this) {
        ProjectTab.Docs -> AppIcon.Docs
        ProjectTab.Repository -> AppIcon.Repository
        ProjectTab.Members -> AppIcon.Members
        ProjectTab.Agent -> AppIcon.Agent
    }

private val ProjectTab.shortLabel: String
    get() = when (this) {
        ProjectTab.Docs -> "문서"
        ProjectTab.Repository -> "저장소"
        ProjectTab.Members -> "멤버"
        ProjectTab.Agent -> "Agent"
    }

private val ProjectTab.displayName: String
    get() = when (this) {
        ProjectTab.Docs -> "Documentation"
        ProjectTab.Repository -> "Repository"
        ProjectTab.Members -> "Members"
        ProjectTab.Agent -> "AI Agent"
    }
