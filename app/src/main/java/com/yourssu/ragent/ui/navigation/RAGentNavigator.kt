package com.yourssu.ragent.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.yourssu.ragent.mock.CurrentUserId
import com.yourssu.ragent.mock.mockMessages
import com.yourssu.ragent.mock.mockPeople
import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Person
import com.yourssu.ragent.model.ProjectInvite
import com.yourssu.ragent.model.ProjectInviteLink
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectTab
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.DataHelpers
import com.yourssu.ragent.ui.ScrollStates
import com.yourssu.ragent.ui.agent.AgentChatScreen
import com.yourssu.ragent.ui.chat.ChatRoute
import com.yourssu.ragent.ui.person.PersonDetailScreen
import com.yourssu.ragent.ui.project.AgentTab
import com.yourssu.ragent.ui.project.AgentViewModel
import com.yourssu.ragent.ui.project.ProjectHomeScreen
import com.yourssu.ragent.ui.project.ProjectInviteDialog
import com.yourssu.ragent.ui.project.ProjectViewModel
import com.yourssu.ragent.ui.projectlist.CreateProjectDialog
import com.yourssu.ragent.ui.projectlist.ProjectListScreen
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RAGentNavigator(
    projectViewModel: ProjectViewModel,
    dataHelpers: DataHelpers,
    scrollStates: ScrollStates,
    messages: List<ChatMessage>,
    pendingInvite: ProjectInvite?,
    joinError: String?,
    onInviteHandled: () -> Unit,
    onPendingInviteChange: (ProjectInvite?) -> Unit,
    onJoinErrorChange: (String?) -> Unit
) {
    val projects = projectViewModel.projects
    val agentViewModel: AgentViewModel = viewModel()
    val context = LocalContext.current
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.ProjectList) }
    var selectedTab by remember { mutableStateOf(ProjectTab.Docs) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Helper 함수들 (AppScreen.Chat의 backFromChat 등)
    fun backFromChat(chat: AppScreen.Chat) {
        screen = when {
            chat.returnToList -> AppScreen.Chat(
                title = chat.project?.name ?: "Messages",
                subtitle = "Inbox",
                project = chat.project,
                listMode = true
            )
            chat.project != null -> AppScreen.ProjectHome(chat.project)
            else -> AppScreen.ProjectList
        }
    }

    when (val current = screen) {
        AppScreen.ProjectList -> ProjectListScreen(
            projects = projects,
            isLoading = projectViewModel.isLoading,
            errorMessage = projectViewModel.loadError,
            onRetry = projectViewModel::loadProjects,
            onProjectClick = {
                selectedTab = ProjectTab.Docs
                screen = AppScreen.ProjectHome(it)
            },
            onProfileClick = {
                val firebaseUser = Firebase.auth.currentUser
                screen = AppScreen.PersonDetail(
                    person = Person(
                        id = firebaseUser?.uid ?: CurrentUserId,
                        name = firebaseUser?.displayName
                            ?: firebaseUser?.email?.substringBefore('@')
                            ?: "나",
                        projects = projects
                    ),
                    returnTo = AppScreen.ProjectList
                )
            },
            onChatClick = { screen = AppScreen.Chat("Messages", "Inbox", listMode = true) },
            onCreateClick = { showCreateDialog = true }
        )

        is AppScreen.ProjectHome -> {
            val project = projects.firstOrNull { it.id == current.project.id } ?: current.project
            ProjectHomeScreen(
                project = project,
                selectedTab = selectedTab,
                isLoading = projectViewModel.isLoading,
                errorMessage = projectViewModel.loadError,
                onRefresh = projectViewModel::loadProjects,
                onTabSelected = { selectedTab = it },
                onBack = {
                    selectedTab = ProjectTab.Docs
                    screen = AppScreen.ProjectList
                },
                personName = dataHelpers.personName,
                membersScrollIndex = scrollStates.members[project.id]?.index ?: 0,
                membersScrollOffset = scrollStates.members[project.id]?.offset ?: 0,
                onMembersScrollPositionChange = { index, offset ->
                    scrollStates.members[project.id] = ScrollPosition(index, offset)
                },
                onProjectChatClick = {
                    screen = AppScreen.Chat(project.name, "Project messages", project = project, listMode = true)
                },
                onMemberChatClick = { member ->
                    screen = AppScreen.Chat(
                        dataHelpers.personName(member.personId),
                        "Project message",
                        project,
                        member
                    )
                },
                onMemberClick = { member ->
                    screen = AppScreen.PersonDetail(
                        person = dataHelpers.personForId(member.personId),
                        returnTo = AppScreen.ProjectHome(project),
                        profileRole = member.role,
                        profileSummary = member.summary
                    )
                },
                onMemberRoleChange = { member, role ->
                    projectViewModel.changeMemberRole(project.id, member, role) { changed ->
                        if (!changed) {
                            Toast.makeText(context, "역할을 변경하지 못했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onMemberDelete = { member ->
                    projectViewModel.deleteMember(project.id, member) { deleted ->
                        if (!deleted) {
                            Toast.makeText(context, "멤버를 삭제하지 못했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCreateInvite = { role, regenerate ->
                    projectViewModel.createInvite(project, role, regenerate) { link ->
                        if (link == null) {
                            Toast.makeText(context, "초대 링크를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                            return@createInvite
                        }
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, link)
                                },
                                "Share invite link"
                            )
                        )
                    }
                },
                onProjectVisibilityChange = { visibility, onResult ->
                    projectViewModel.changeProjectVisibility(project.id, visibility, onResult)
                },
                onDeleteProject = {
                    projectViewModel.deleteProject(project) { deleted ->
                        if (deleted) {
                            // messages에서 해당 프로젝트 메시지 제거 (messages는 RAGentApp에서 관리)
                            selectedTab = ProjectTab.Docs
                            screen = AppScreen.ProjectList
                        }
                    }
                },
                onLeaveProject = {
                    projectViewModel.leaveProject(project.id) { left ->
                        if (left) {
                            selectedTab = ProjectTab.Docs
                            screen = AppScreen.ProjectList
                        }
                    }
                },
                onAgentSessionClick = { session ->
                    agentViewModel.selectSession(project.id, session.id)
                    screen = AppScreen.AgentChat(project)
                }
            )
        }

        is AppScreen.AgentChat -> {
            AgentChatScreen(
                project = current.project,
                viewModel = agentViewModel,
                onBack = { screen = AppScreen.ProjectHome(current.project) }
            )
        }

        is AppScreen.PersonDetail -> {
            val firebaseUser = Firebase.auth.currentUser
            val isCurrentUser = current.person.id == CurrentUserId ||
                current.person.id == firebaseUser?.uid
            val profilePerson = if (isCurrentUser) {
                current.person.copy(
                    id = firebaseUser?.uid ?: current.person.id,
                    name = firebaseUser?.displayName
                        ?: firebaseUser?.email?.substringBefore('@')
                        ?: current.person.name,
                    projects = projects
                )
            } else {
                current.person
            }

            PersonDetailScreen(
                person = profilePerson,
                profileRole = current.profileRole,
                profileSummary = current.profileSummary,
                isCurrentUser = isCurrentUser,
                usageDashboard = agentViewModel.usageDashboard,
                onBack = { screen = current.returnTo }
            )
        }

        is AppScreen.Chat -> ChatRoute(
            chat = current,
            messages = messages.filterFor(current),
            personName = dataHelpers.personName,
            personRole = dataHelpers.personRole,
            projectName = dataHelpers.projectName,
            listScrollPosition = scrollStates.chatList["list:${current.project?.id ?: "all"}"] ?: ScrollPosition(),
            directScrollPosition = scrollStates.directMessage[
                "dm:${current.project?.id ?: "all"}:${current.member?.personId ?: current.title}"
            ] ?: ScrollPosition(index = (messages.filterFor(current).size - 1).coerceAtLeast(0)),
            onListScrollChange = { index, offset ->
                scrollStates.chatList["list:${current.project?.id ?: "all"}"] = ScrollPosition(index, offset)
            },
            onDirectScrollChange = { index, offset ->
                scrollStates.directMessage[
                    "dm:${current.project?.id ?: "all"}:${current.member?.personId ?: current.title}"
                ] = ScrollPosition(index, offset)
            },
            onBack = { backFromChat(current) },
            onPersonClick = { personId ->
                screen = AppScreen.PersonDetail(
                    dataHelpers.personForId(personId),
                    returnTo = current
                )
            },
            onMessageClick = { message ->
                val personId = if (message.senderId == CurrentUserId) message.receiverId else message.senderId
                screen = AppScreen.Chat(
                    title = dataHelpers.personName(personId),
                    subtitle = "Message detail",
                    project = current.project,
                    member = ProjectMember("member-dm-$personId", personId, dataHelpers.personRole(personId), "DM"),
                    returnToList = true
                )
            }
        )
    }

    // 다이얼로그들
    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { project, onResult ->
                projectViewModel.createProject(project) { created ->
                    if (created) {
                        selectedTab = ProjectTab.Docs
                        screen = AppScreen.ProjectHome(project)
                    }
                    onResult(created)
                }
            }
        )
    }

    pendingInvite?.let { invite ->
        ProjectInviteDialog(
            invite = invite,
            errorMessage = joinError,
            onJoin = {
                onJoinErrorChange(null)
                projectViewModel.joinProject(invite) { joined ->
                    if (joined) {
                        onPendingInviteChange(null)
                        onInviteHandled()
                    } else {
                        onJoinErrorChange("Unable to join this project.")
                    }
                }
            },
            onDismiss = {
                onPendingInviteChange(null)
                onInviteHandled()
            }
        )
    }
}

private fun List<ChatMessage>.filterFor(screen: AppScreen.Chat) = filter { message ->
    val projectMatches = screen.project == null || message.projectId == screen.project.id
    val memberMatches = screen.member == null || message.senderId == screen.member.personId ||
            message.receiverId == screen.member.personId
    projectMatches && memberMatches
}
