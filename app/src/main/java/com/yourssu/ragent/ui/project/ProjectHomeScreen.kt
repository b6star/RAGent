package com.yourssu.ragent.ui.project

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectTab
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.Role
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
    onRefresh: () -> Unit,
    onTabSelected: (ProjectTab) -> Unit,
    onBack: () -> Unit,
    personName: (String) -> String,
    membersScrollIndex: Int,
    membersScrollOffset: Int,
    onMembersScrollPositionChange: (Int, Int) -> Unit,
    onProjectChatClick: () -> Unit,
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

    LaunchedEffect(isLoading) {
        if (wasLoading && !isLoading && errorMessage == null) {
            showRefreshComplete = true
            delay(1000)
            showRefreshComplete = false
        }
        wasLoading = isLoading
    }

    BackHandler(
        enabled = selectedTab != ProjectTab.Docs && selectedTab != ProjectTab.Repository,
        onBack = onBack
    )

    Box(modifier = Modifier.fillMaxSize()) {
        val padding = PaddingValues(0.dp)
        val content: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                verticalArrangement = Arrangement.spacedBy(if (selectedTab == ProjectTab.Docs || selectedTab == ProjectTab.Repository) 4.dp else 18.dp)
            ) {
                ProjectHeader(
                    projectName = project.name,
                    horizontalPadding = 16.dp,
                    onBack = onBack,
                    onDetailsClick = { showDetails = true },
                    onChatClick = onProjectChatClick
                )
                Box(Modifier.fillMaxSize()) {
                    DocsTab(project, onBack, visible = selectedTab == ProjectTab.Docs)
                    RepositoryTab(project, onBack, visible = selectedTab == ProjectTab.Repository)
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
                }
                return@Column
                /*
                when (selectedTab) {
                    ProjectTab.Docs, ProjectTab.Repository -> {
                        Box(Modifier.fillMaxSize()) {
                            DocsTab(project, onBack, visible = selectedTab == ProjectTab.Docs)
                            RepositoryTab(project, onBack, visible = selectedTab == ProjectTab.Repository)
                        }
                    }
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
                    ProjectTab.Agent -> AgentTab(
                        project = project,
                        onSessionClick = onAgentSessionClick
                    )
                }
                */
            }
        }

        if (false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .let {
                        if (selectedTab == ProjectTab.Docs || selectedTab == ProjectTab.Repository) {
                            it
                        } else it.padding(padding)
                    }
            ) {
                content(Modifier)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                content(Modifier)

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ProjectBottomBar(selectedTab, onTabSelected)
        }
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
private fun ProjectHeader(
    projectName: String,
    horizontalPadding: Dp,
    onBack: () -> Unit,
    onDetailsClick: () -> Unit,
    onChatClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(onClick = onBack, icon = AppIcon.Back)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = projectName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CircleButton(onClick = onChatClick, icon = AppIcon.ChatList)
        Spacer(Modifier.width(10.dp))
        CircleButton(onClick = onDetailsClick, icon = AppIcon.More)
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
        Surface(modifier = Modifier.widthIn(max = 480.dp), shape = RoundedCornerShape(34.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ProjectTab.entries.forEach { tab -> BottomBarItem(tab, selectedTab == tab) { onTabSelected(tab) } }
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(tab: ProjectTab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent) {
        Column(modifier = Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RAGentIcon(tab.icon, color)
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
