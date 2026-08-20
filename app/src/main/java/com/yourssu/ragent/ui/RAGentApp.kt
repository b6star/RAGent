package com.yourssu.ragent.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourssu.ragent.model.ChatMessage
import com.yourssu.ragent.model.Person
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectTab
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.chat.ChatListScreen
import com.yourssu.ragent.ui.chat.DirectMessageScreen
import com.yourssu.ragent.ui.person.PersonDetailScreen
import com.yourssu.ragent.ui.project.ProjectHomeScreen
import com.yourssu.ragent.ui.project.ProjectViewModel
import com.yourssu.ragent.ui.projectlist.CreateProjectDialog
import com.yourssu.ragent.ui.projectlist.ProjectListScreen

private sealed interface AppScreen {
    data object ProjectList : AppScreen
    data class ProjectHome(val project: Project) : AppScreen
    data class Chat(
        val title: String,
        val subtitle: String,
        val project: Project? = null,
        val member: ProjectMember? = null,
        val listMode: Boolean = false,
        val returnToList: Boolean = false
    ) : AppScreen
    data class PersonDetail(
        val person: Person,
        val returnTo: AppScreen,
        val profileRole: Role? = null,
        val profileSummary: String? = null
    ) : AppScreen
}

private data class ScrollPosition(val index: Int = 0, val offset: Int = 0)

@Composable
fun RAGentApp() {
    val projectViewModel: ProjectViewModel = viewModel()
    val projects = projectViewModel.projects

    LaunchedEffect(Unit) {
        projectViewModel.loadProjects()
    }
    val people = remember {
        listOf(
            Person(id = "uid-me", name = "나"),
            Person(id = "uid-junseong", name = "준성"),
            Person(id = "uid-minji", name = "민지"),
            Person(id = "uid-doyun", name = "도윤"),
            Person(id = "uid-seoyeon", name = "서연")
        )
    }
    val messages = remember {
        mutableStateListOf(
            mockMessage("msg-1", "안녕하세요.", "uid-junseong", "uid-me","project-ragent", minute = 9 * 60 + 3, isNotice = true),
            mockMessage("msg-2", "이번 주 변경사항은 Docs 탭부터 정리합시다.", "uid-junseong", "uid-me", "project-ragent", minute = 9 * 60 + 30, isNotice = true),
            mockMessage("msg-3", "네, Docs Mock 먼저 정리해두겠습니다.", "uid-me", "uid-junseong", "project-ragent", minute = 9 * 60 + 45),
            mockMessage("msg-4", "Repository 화면은 README 우선 노출로 가죠.", "uid-junseong", "uid-me", "project-ragent", minute = 10 * 60 + 2),
            mockMessage("msg-5", "성적 탭 Mock 데이터 확인 부탁드립니다.", "uid-minji", "uid-me", "project-focuswave", minute = 10 * 60 + 4),
            mockMessage("msg-6", "확인했습니다. 리스트 스크롤 케이스도 추가할게요.", "uid-me", "uid-minji", "project-focuswave", minute = 10 * 60 + 20),
            mockMessage("msg-7", "보낸 메시지 탭에서도 레이아웃 확인 부탁드립니다.", "uid-minji", "uid-me", "project-focuswave", minute = 17 * 60 + 24),
            mockMessage("msg-8", "채플 탭 담당 범위 공유했습니다.", "uid-doyun", "uid-me", "project-ragent", minute = 11 * 60 + 18),
            mockMessage("msg-9", "멤버 탭은 관리자/팀원만 보이게 처리했습니다.", "uid-me", "uid-doyun", "project-ragent", minute = 13 * 60 + 42),
            mockMessage("msg-10", "채팅 상세 화면 스크롤 테스트용 메시지입니다.", "uid-doyun", "uid-me", "project-ragent", minute = 17 * 60 + 31),
            mockMessage("msg-11", "문서 접근 권한 확인 부탁드립니다.", "uid-seoyeon", "uid-me", "project-soongsil-life", minute = 12 * 60 + 2),
            mockMessage("msg-12", "열람자 권한은 문서 확인 중심으로 잡아둘게요.", "uid-me", "uid-seoyeon", "project-soongsil-life", minute = 12 * 60 + 30),
            mockMessage("msg-13", "메모: Members 탭 긴 담당 문구 줄바꿈 확인", "uid-me", "uid-me", null, minute = 20 * 60 + 10),
            mockMessage("msg-14", "메모: 전체 메시지 받은/보낸 토글 스크롤 상태 확인", "uid-me", "uid-me", null, minute = 20 * 60 + 12),
            mockMessage("msg-15", "메모: Firebase 연결 전까지 전송 버튼은 입력값만 비우기", "uid-me", "uid-me", null, minute = 20 * 60 + 15),
            mockMessage("msg-16", "메모: 프로젝트 생성 바텀시트 디자인 점검", "uid-me", "uid-me", null, minute = 20 * 60 + 18)
        )
    }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.ProjectList) }
    var selectedTab by remember { mutableStateOf(ProjectTab.Docs) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val chatListScrollPositions = remember { mutableStateMapOf<String, ScrollPosition>() }
    val directMessageScrollPositions = remember { mutableStateMapOf<String, ScrollPosition>() }
    val membersScrollPositions = remember { mutableStateMapOf<String, ScrollPosition>() }

    fun personName(personId: String): String = people.firstOrNull { it.id == personId }?.name ?: personId

    fun personRole(personId: String): Role = projects
        .flatMap { it.members }
        .firstOrNull { it.personId == personId }
        ?.role ?: if (personId == "uid-me") Role.Admin else Role.Viewer

    fun projectName(projectId: String?): String? = projects.firstOrNull { it.id == projectId }?.name

    fun chatListKey(chat: AppScreen.Chat): String = "list:${chat.project?.id ?: "all"}"

    fun directMessageKey(chat: AppScreen.Chat): String = "dm:${chat.project?.id ?: "all"}:${chat.member?.personId ?: chat.title}"

    fun personForId(personId: String): Person {
        val joinedProjects = projects.filter { project -> project.members.any { it.personId == personId && it.role != Role.Viewer } }
        return people.firstOrNull { it.id == personId }?.copy(projects = joinedProjects)
            ?: Person(id = personId, name = personId, projects = joinedProjects)
    }

    fun backFromChat(chat: AppScreen.Chat) {
        screen = when {
            chat.returnToList -> AppScreen.Chat(
                title = chat.project?.name ?: "전체 메시지",
                subtitle = "받은 메시지",
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
            onProjectClick = {
                selectedTab = ProjectTab.Docs
                screen = AppScreen.ProjectHome(it)
            },
            onChatClick = { screen = AppScreen.Chat("전체 메시지", "받은 메시지", listMode = true) },
            onCreateClick = { showCreateDialog = true }
        )
        is AppScreen.ProjectHome -> ProjectHomeScreen(
            project = current.project,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onBack = {
                selectedTab = ProjectTab.Docs
                screen = AppScreen.ProjectList
            },
            personName = ::personName,
            membersScrollIndex = membersScrollPositions[current.project.id]?.index ?: 0,
            membersScrollOffset = membersScrollPositions[current.project.id]?.offset ?: 0,
            onMembersScrollPositionChange = { index, offset ->
                membersScrollPositions[current.project.id] = ScrollPosition(index, offset)
            },
            onProjectChatClick = {
                screen = AppScreen.Chat(current.project.name, "프로젝트 관련 DM", project = current.project, listMode = true)
            },
            onMemberChatClick = { member ->
                screen = AppScreen.Chat(personName(member.personId), "${current.project.name} 관련 DM", project = current.project, member = member)
            },
            onMemberClick = { member ->
                screen = AppScreen.PersonDetail(
                    person = personForId(member.personId),
                    returnTo = AppScreen.ProjectHome(current.project),
                    profileRole = member.role,
                    profileSummary = member.summary
                )
            },
            onDeleteProject = {
                projectViewModel.deleteProject(current.project) { deleted ->
                    if (deleted) {
                        messages.removeAll { it.projectId == current.project.id }
                        selectedTab = ProjectTab.Docs
                        screen = AppScreen.ProjectList
                    }
                }
            },
            onLeaveProject = {}
        )
        is AppScreen.PersonDetail -> PersonDetailScreen(
            person = current.person,
            profileRole = current.profileRole,
            profileSummary = current.profileSummary,
            onBack = { screen = current.returnTo }
        )
        is AppScreen.Chat -> {
            val visibleMessages = messages.filterFor(current)
            if (current.listMode) {
                val scrollKey = chatListKey(current)
                val scrollPosition = chatListScrollPositions[scrollKey] ?: ScrollPosition()
                ChatListScreen(
                    title = current.title,
                    messages = visibleMessages,
                    currentUserId = CurrentUserId,
                    personName = ::personName,
                    personRole = ::personRole,
                    projectName = ::projectName,
                    scrollIndex = scrollPosition.index,
                    scrollOffset = scrollPosition.offset,
                    onScrollPositionChange = { index, offset ->
                        chatListScrollPositions[scrollKey] = ScrollPosition(index, offset)
                    },
                    onBack = { backFromChat(current) },
                    onNameClick = { personId ->
                        screen = AppScreen.PersonDetail(personForId(personId), returnTo = current)
                    },
                    onMessageClick = { message ->
                        val targetPersonId = if (message.senderId == CurrentUserId) message.receiverId else message.senderId
                        screen = AppScreen.Chat(
                            title = personName(targetPersonId),
                            subtitle = "메시지 상세",
                            project = current.project,
                            member = ProjectMember(id = "member-dm-$targetPersonId", personId = targetPersonId, role = personRole(targetPersonId), summary = "DM"),
                            returnToList = true
                        )
                    }
                )
            } else {
                val scrollKey = directMessageKey(current)
                val scrollPosition = directMessageScrollPositions[scrollKey]
                    ?: ScrollPosition(index = (visibleMessages.size - 1).coerceAtLeast(0))
                DirectMessageScreen(
                    title = current.title,
                    subtitle = current.subtitle,
                    messages = visibleMessages,
                    highlightedProjectId = current.project?.id,
                    currentUserId = CurrentUserId,
                    personName = ::personName,
                    personRole = ::personRole,
                    projectName = ::projectName,
                    isSelfChat = current.member?.personId == CurrentUserId,
                    scrollIndex = scrollPosition.index,
                    scrollOffset = scrollPosition.offset,
                    onScrollPositionChange = { index, offset ->
                        directMessageScrollPositions[scrollKey] = ScrollPosition(index, offset)
                    },
                    onBack = { backFromChat(current) },
                    onNameClick = { personId ->
                        screen = AppScreen.PersonDetail(personForId(personId), returnTo = current)
                    }
                )
            }
        }
    }

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
}

private const val CurrentUserId = "uid-me"

private fun mockMessage(
    id: String,
    text: String,
    senderId: String,
    receiverId: String,
    projectId: String?,
    minute: Int,
    isNotice: Boolean = false
) = ChatMessage(
    id = id,
    text = text,
    createdAt = minute * 60_000L,
    senderId = senderId,
    receiverId = receiverId,
    projectId = projectId,
    isNotice = isNotice
)

private fun List<ChatMessage>.filterFor(screen: AppScreen.Chat): List<ChatMessage> = filter { message ->
    val projectMatches = screen.project == null || message.projectId == screen.project.id
    val memberMatches = screen.member == null || message.senderId == screen.member.personId || message.receiverId == screen.member.personId
    projectMatches && memberMatches
}
