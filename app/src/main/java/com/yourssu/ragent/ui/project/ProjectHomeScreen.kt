package com.yourssu.ragent.ui.project

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectTab
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.layout.ScreenPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHomeScreen(
    project: Project,
    selectedTab: ProjectTab,
    onTabSelected: (ProjectTab) -> Unit,
    onBack: () -> Unit,
    personName: (String) -> String,
    membersScrollIndex: Int,
    membersScrollOffset: Int,
    onMembersScrollPositionChange: (Int, Int) -> Unit,
    onProjectChatClick: () -> Unit,
    onMemberChatClick: (ProjectMember) -> Unit,
    onMemberClick: (ProjectMember) -> Unit,
    onDeleteProject: () -> Unit,
    onLeaveProject: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Scaffold(bottomBar = { ProjectBottomBar(selectedTab, onTabSelected) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ProjectHeader(
                projectName = project.name,
                tabName = selectedTab.displayName,
                onBack = onBack,
                onDetailsClick = { showDetails = true },
                onChatClick = onProjectChatClick
            )
            when (selectedTab) {
                ProjectTab.Docs -> DocsTab()
                ProjectTab.Repository -> RepositoryTab(project)
                ProjectTab.Members -> MembersTab(
                    members = project.members,
                    personName = personName,
                    scrollIndex = membersScrollIndex,
                    scrollOffset = membersScrollOffset,
                    onScrollPositionChange = onMembersScrollPositionChange,
                    onMemberChatClick = onMemberChatClick,
                    onMemberClick = onMemberClick
                )
                ProjectTab.Agent -> AgentTab()
            }
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
    tabName: String,
    onBack: () -> Unit,
    onDetailsClick: () -> Unit,
    onChatClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(onClick = onBack, icon = AppIcon.Back)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = projectName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = tabName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        CircleButton(onClick = onChatClick, icon = AppIcon.ChatList)
        Spacer(Modifier.width(10.dp))
        CircleButton(onClick = onDetailsClick, icon = AppIcon.More)
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, icon: AppIcon) {
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
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
            Text(tab.shortLabel, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
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
