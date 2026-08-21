package com.yourssu.ragent.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ProjectDocument(
    val projectId: String = "",
    val name: String = "",
    val ownerId: String = "",
    val githubUrl: String = "",
    val docsUrl: String = "",
    val visibility: String = ProjectVisibility.Public.name,
    val status: String = ProjectStatus.IN_PROGRESS.name,
    @get:ServerTimestamp val createdAt: Timestamp? = null,
    @get:ServerTimestamp val updatedAt: Timestamp? = null
)

data class ProjectMemberDocument(
    val userId: String = "",
    val displayName: String = "",
    val role: String = Role.Viewer.name,
    val summary: String = "",
    val inviteId: String = ""
)

data class ProjectInviteDocument(
    val projectId: String = "",
    val projectName: String = "",
    val role: String = Role.Viewer.name,
    val createdBy: String = "",
    @get:ServerTimestamp val createdAt: Timestamp? = null
)

data class ProjectInvite(
    val projectId: String,
    val inviteId: String,
    val projectName: String,
    val role: Role
)

data class ProjectInviteLink(
    val projectId: String,
    val inviteId: String
)

const val InviteLinkHost = "ragent-d6b01.web.app"

fun ProjectDocument.toProject(
    id: String,
    role: Role,
    members: List<ProjectMember> = emptyList()
): Project {
    return Project(
        id = projectId.ifBlank { id },
        name = name,
        myRole = role,
        githubUrl = githubUrl,
        docsUrl = docsUrl,
        visibility = ProjectVisibility.entries.firstOrNull { it.name == visibility }
            ?: ProjectVisibility.Public,
        members = members,
        status = ProjectStatus.entries.firstOrNull { it.name == status }
            ?: ProjectStatus.IN_PROGRESS
    )
}

fun ProjectMemberDocument.toProjectMember(id: String): ProjectMember {
    return ProjectMember(
        id = id,
        personId = userId,
        role = Role.entries.firstOrNull { it.name == role } ?: Role.Viewer,
        summary = summary,
        name = displayName
    )
}
