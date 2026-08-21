package com.yourssu.ragent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourssu.ragent.mock.CurrentUserId
import com.yourssu.ragent.mock.mockMessages
import com.yourssu.ragent.mock.mockPeople
import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Person
import com.yourssu.ragent.model.ProjectInvite
import com.yourssu.ragent.model.ProjectInviteLink
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.navigation.RAGentNavigator
import com.yourssu.ragent.ui.navigation.ScrollPosition
import com.yourssu.ragent.ui.project.ProjectViewModel

@Composable
fun RAGentApp(
    inviteLink: ProjectInviteLink? = null,
    onInviteHandled: () -> Unit = {}
) {
    // ViewModel 및 의존성 생성
    val projectViewModel: ProjectViewModel = viewModel()
    val people = remember { mockPeople() }
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(mockMessages()) } }

    // 스크롤 위치 상태 - Navigator와 공유
    val scrollStates = remember {
        ScrollStates(
            chatList = mutableStateMapOf(),
            directMessage = mutableStateMapOf(),
            members = mutableStateMapOf()
        )
    }

    // 초대 링크 처리
    var pendingInvite by remember { mutableStateOf<ProjectInvite?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        projectViewModel.loadProjects()
    }

    LaunchedEffect(inviteLink) {
        inviteLink?.let { link ->
            projectViewModel.resolveInvite(link.projectId, link.inviteId) { invite ->
                pendingInvite = invite
                joinError = null
                if (invite == null) onInviteHandled()
            }
        }
    }

    // 데이터 헬퍼 함수들
    val dataHelpers = remember(people, projectViewModel.projects) {
        DataHelpers(
            personName = { personId ->
                people.firstOrNull { it.id == personId }?.name ?: personId
            },
            personRole = { personId ->
                projectViewModel.projects.flatMap { it.members }
                    .firstOrNull { it.personId == personId }?.role
                    ?: if (personId == CurrentUserId) Role.Admin else Role.Viewer
            },
            projectName = { projectId ->
                projectViewModel.projects.firstOrNull { it.id == projectId }?.name
            },
            personForId = { personId ->
                val joinedProjects = projectViewModel.projects.filter { project ->
                    project.members.any { it.personId == personId && it.role != Role.Viewer }
                }
                people.firstOrNull { it.id == personId }?.copy(projects = joinedProjects)
                    ?: Person(id = personId, name = personId, projects = joinedProjects)
            }
        )
    }

    RAGentNavigator(
        projectViewModel = projectViewModel,
        dataHelpers = dataHelpers,
        scrollStates = scrollStates,
        messages = messages,
        pendingInvite = pendingInvite,
        joinError = joinError,
        onInviteHandled = onInviteHandled,
        onPendingInviteChange = { pendingInvite = it },
        onJoinErrorChange = { joinError = it }
    )
}

// 상태 클래스들
data class ScrollStates(
    val chatList: SnapshotStateMap<String, ScrollPosition>,
    val directMessage: SnapshotStateMap<String, ScrollPosition>,
    val members: SnapshotStateMap<String, ScrollPosition>
)

data class DataHelpers(
    val personName: (String) -> String,
    val personRole: (String) -> Role,
    val projectName: (String?) -> String?,
    val personForId: (String) -> Person
)
