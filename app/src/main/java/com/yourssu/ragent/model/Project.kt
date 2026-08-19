package com.yourssu.ragent.model

data class Project(
    val id: String,
    val name: String,
    val myRole: Role,
    val githubUrl: String = "",
    val docsUrl: String = "",
    val visibility: ProjectVisibility = ProjectVisibility.Public,
    val latestPullRequest: PullRequest = defaultPullRequest(id, name),
    val members: List<ProjectMember> = defaultMembers(),
    val status: ProjectStatus = ProjectStatus.IN_PROGRESS
)

data class ProjectMember(
    val id: String,
    val personId: String,
    val role: Role,
    val summary: String
)

data class Person(
    val id: String, // 추후 Firebase Auth UID 사용
    val name: String,
    val projects: List<Project> = emptyList(),
    val pullRequests: List<PullRequest> = emptyList(),
    val ongoingWorkIds: List<String> = emptyList()
) {
    val latestPullRequest: PullRequest?
        get() = pullRequests
            .maxByOrNull { it.updatedAt }
}

data class ChatMessage(
    val id: String,
    val text: String,
    val createdAt: Long,
    val senderId: String,
    val receiverId: String,
    val projectId: String? = null,
    val isNotice: Boolean = false
)

data class PullRequest(
    val projectId: String,
    val projectName: String,
    val title: String,
    val branchName: String,
    val author: ProjectMember,
    val updatedAt: Long,
    val number: Int,
    val url: String,
    val prStatus: PrStatus
)

data class OngoingWork(
    val id: String,
    val projectId: String,
    val projectName: String,
    val title: String,
    val description: String? = null,

    // ProjectMember.id 참조
    val assigneeMemberId: String,
    val assignerMemberId: String = assigneeMemberId,
    val participantMemberIds: List<String> = emptyList(),

    // Unix timestamp (milliseconds)
    val createdAt: Long,
    val dueAt: Long? = null,

    val visibility: OngoingWorkVisibility = OngoingWorkVisibility.Public,
    val status: TaskStatus
) {
    val allParticipantMemberIds: List<String>
        get() = (
                listOf(assigneeMemberId) +
                        participantMemberIds
                ).distinct()
}

enum class OngoingWorkVisibility(val label: String) {
    Public("Public"),
    Private("Private")
}

enum class ProjectVisibility(val label: String) {
    Public("Public"),
    Private("Private")
}

enum class Role(val label: String) {
    Admin("관리자"),
    Member("기여자"),
    Viewer("열람자")
}

enum class ProjectTab(val label: String) {
    Docs("Docs"),
    Repository("Repository"),
    Members("Members"),
    Agent("Agent")
}

enum class TaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

enum class PrStatus {
    OPEN,
    MERGED,
    CLOSED
}

enum class ProjectStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

fun defaultMembers() = listOf(
    ProjectMember(
        id = "member-junseong",
        personId = "uid-junseong",
        role = Role.Admin,
        summary = "프로젝트 리드 및 PR 검토"
    ),
    ProjectMember(
        id = "member-minji",
        personId = "uid-minji",
        role = Role.Member,
        summary = "성적 탭 담당"
    ),
    ProjectMember(
        id = "member-doyun",
        personId = "uid-doyun",
        role = Role.Member,
        summary = "등록금 탭 담당"
    ),
    ProjectMember(
        id = "member-seoyeon",
        personId = "uid-seoyeon",
        role = Role.Viewer,
        summary = "문서 열람"
    )
)

private fun defaultPullRequest(
    projectId: String,
    projectName: String
) = PullRequest(
    projectId = projectId,
    projectName = projectName,
    title = "feature/grade-api-refresh",
    branchName = "feature/grade-api-refresh",
    author = defaultMembers()[1],

    // Mock timestamp
    updatedAt = System.currentTimeMillis(),

    number = 42,
    url = "https://github.com/yourssu/" +
            "${projectName.lowercase().replace(" ", "-")}/pull/42",
    prStatus = PrStatus.OPEN
)
