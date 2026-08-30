package com.yourssu.ragent.ui.project

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectDocument
import com.yourssu.ragent.model.ProjectInvite
import com.yourssu.ragent.model.ProjectInviteDocument
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectMemberDocument
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.PublicSourceUrl
import com.yourssu.ragent.model.SourceUrlValidation
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.model.toProject
import com.yourssu.ragent.model.toProjectMember
import com.yourssu.ragent.data.remote.RAGentFunctions
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProjectViewModel : ViewModel() {
    private val functions = RAGentFunctions.instance
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    fun loadProjects() {
        val uid = Firebase.auth.currentUser?.uid ?: run {
            loadError = "로그인 사용자 정보를 확인하지 못했습니다."
            return
        }
        viewModelScope.launch {
            isLoading = true
            loadError = null
            try {
                projects = fetchProjects(uid)
            } catch (e: Exception) {
                Log.e("ProjectLoad", "Failed to load projects", e)
                loadError = "프로젝트를 불러오지 못했습니다."
            } finally {
                isLoading = false
            }
        }
    }

    /** Requests a throttled background refresh when a project is opened. */
    fun requestSourceSync(projectId: String) {
        if (projects.none { it.id == projectId }) {
            Log.d("SourceSync", "requestSourceSync skipped: project not loaded id=$projectId")
            return
        }
        viewModelScope.launch {
            Log.d("SourceSync", "requestSourceSync started id=$projectId")
            try {
                val result = functions.getHttpsCallable("requestSourceSync")
                    .call(mapOf("projectId" to projectId))
                    .await()
                Log.d(
                    "SourceSync",
                    "requestSourceSync succeeded id=$projectId data=${result.data}"
                )
            } catch (e: Exception) {
                Log.w("SourceSync", "Background Source sync request failed", e)
            }
        }
    }

    fun createProject(project: Project, onResult: (Boolean) -> Unit) {
        val user = Firebase.auth.currentUser ?: run {
            onResult(false)
            return
        }
        val projectRef = Firebase.firestore.collection("projects").document(project.id)
        val sourceValidation = PublicSourceUrl.validate(project.githubUrl, project.docsUrl)
        if (sourceValidation !is SourceUrlValidation.Valid) {
            onResult(false)
            return
        }

        viewModelScope.launch {
            try {
                projectRef.set(
                    ProjectDocument(
                        projectId = project.id,
                        name = project.name,
                        ownerId = user.uid,
                        githubUrl = sourceValidation.githubUrl,
                        docsUrl = sourceValidation.notionUrl,
                        visibility = project.visibility.name,
                        status = project.status.name
                    )
                ).await()

                val owner = ProjectMemberDocument(
                    userId = user.uid,
                    displayName = user.displayName.orEmpty(),
                    role = Role.Admin.name
                )
                projectRef.collection("members").document(user.uid).set(owner).await()
                projects = listOf(
                    project.copy(
                        myRole = Role.Admin,
                        githubUrl = sourceValidation.githubUrl,
                        docsUrl = sourceValidation.notionUrl,
                        members = listOf(owner.toProjectMember(user.uid))
                    )
                ) + projects
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectCreate", "Failed to create project", e)
                onResult(false)
            }
        }
    }

    fun updateSourceLinks(
        projectId: String,
        githubUrl: String,
        notionUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val validation = PublicSourceUrl.validate(githubUrl, notionUrl)
        val valid = validation as? SourceUrlValidation.Valid ?: run {
            val message = when (validation) {
                SourceUrlValidation.InvalidGithub -> "GitHub 공개 Repository URL을 확인해 주세요."
                SourceUrlValidation.InvalidNotion -> "Notion 공개 페이지 URL을 확인해 주세요."
                is SourceUrlValidation.Valid -> null
            }
            onResult(false, message)
            return
        }
        if (projects.firstOrNull { it.id == projectId }?.myRole != Role.Admin) {
            onResult(false, "관리자만 Source 링크를 변경할 수 있습니다.")
            return
        }
        viewModelScope.launch {
            try {
                Firebase.firestore.collection("projects").document(projectId)
                    .update(
                        mapOf(
                            "githubUrl" to valid.githubUrl,
                            "docsUrl" to valid.notionUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                projects = projects.map { project ->
                    if (project.id == projectId) {
                        project.copy(githubUrl = valid.githubUrl, docsUrl = valid.notionUrl)
                    } else project
                }
                onResult(true, null)
            } catch (e: Exception) {
                Log.e("ProjectSource", "Failed to update source links", e)
                onResult(false, "Source 링크를 저장하지 못했습니다.")
            }
        }
    }

    fun deleteProject(project: Project, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val firestore = Firebase.firestore
                val projectRef = firestore.collection("projects").document(project.id)
                val childDocuments = listOf("members", "invites", "comments").flatMap { collection ->
                    projectRef.collection(collection).get().await().documents
                }

                // ponytail: Phase 2 uses one atomic client batch; move cleanup to Cloud Functions at 500+ documents.
                check(childDocuments.size <= 499) { "Project contains too many documents for client deletion" }
                firestore.batch().apply {
                    childDocuments.forEach { delete(it.reference) }
                    delete(projectRef)
                }.commit().await()
                projects = projects.filterNot { it.id == project.id }
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectDelete", "Failed to delete project", e)
                onResult(false)
            }
        }
    }

    fun changeProjectVisibility(
        projectId: String,
        visibility: ProjectVisibility,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Firebase.firestore.collection("projects").document(projectId)
                    .update(
                        mapOf(
                            "visibility" to visibility.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
                projects = projects.map {
                    if (it.id == projectId) it.copy(visibility = visibility) else it
                }
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectVisibility", "Failed to update project visibility", e)
                onResult(false)
            }
        }
    }

    fun createInvite(
        project: Project,
        role: Role,
        regenerate: Boolean,
        onResult: (String?) -> Unit
    ) {
        if (project.myRole != Role.Admin || role == Role.Admin) {
            onResult(null)
            return
        }
        val user = Firebase.auth.currentUser ?: run {
            onResult(null)
            return
        }

        viewModelScope.launch {
            try {
                val firestore = Firebase.firestore
                val projectRef = Firebase.firestore.collection("projects").document(project.id)
                val ownerMemberRef = projectRef.collection("members").document(user.uid)
                if (!ownerMemberRef.get().await().exists()) {
                    ownerMemberRef.set(
                        ProjectMemberDocument(
                            userId = user.uid,
                            displayName = user.displayName.orEmpty(),
                            role = Role.Admin.name
                        )
                    ).await()
                }
                val inviteDocuments = projectRef.collection("invites")
                    .whereEqualTo("role", role.name)
                    .get().await().documents
                val activeInvite = inviteDocuments.maxByOrNull {
                    it.getTimestamp("createdAt")?.seconds ?: 0L
                }

                if (!regenerate && activeInvite != null) {
                    if (inviteDocuments.size > 1) {
                        firestore.batch().apply {
                            inviteDocuments.filterNot { it.id == activeInvite.id }
                                .forEach { delete(it.reference) }
                        }.commit().await()
                    }
                    onResult("https://ragent-d6b01.web.app/invite?projectId=${project.id}&inviteId=${activeInvite.id}")
                    return@launch
                }

                val inviteId = UUID.randomUUID().toString()
                firestore.batch().apply {
                    inviteDocuments.forEach { delete(it.reference) }
                    set(
                        projectRef.collection("invites").document(inviteId),
                        ProjectInviteDocument(
                            projectId = project.id,
                            projectName = project.name,
                            role = role.name,
                            createdBy = user.uid
                        )
                    )
                }.commit().await()
                onResult("https://ragent-d6b01.web.app/invite?projectId=${project.id}&inviteId=$inviteId")
            } catch (e: Exception) {
                Log.e("ProjectInvite", "Failed to create invite", e)
                onResult(null)
            }
        }
    }

    fun resolveInvite(projectId: String, inviteId: String, onResult: (ProjectInvite?) -> Unit) {
        viewModelScope.launch {
            try {
                val projectRef = Firebase.firestore.collection("projects").document(projectId)
                val invite = projectRef.collection("invites").document(inviteId)
                    .get().await().toObject(ProjectInviteDocument::class.java)
                    ?: return@launch onResult(null)
                val role = Role.entries.firstOrNull { it.name == invite.role }
                    ?.takeIf { it != Role.Admin }
                    ?: return@launch onResult(null)
                if (invite.projectId != projectId) return@launch onResult(null)

                onResult(ProjectInvite(projectId, inviteId, invite.projectName, role))
            } catch (e: Exception) {
                Log.e("ProjectInvite", "Failed to resolve invite", e)
                onResult(null)
            }
        }
    }

    fun joinProject(invite: ProjectInvite, onResult: (Boolean) -> Unit) {
        val user = Firebase.auth.currentUser ?: run {
            onResult(false)
            return
        }
        val memberRef = Firebase.firestore.collection("projects").document(invite.projectId)
            .collection("members").document(user.uid)

        viewModelScope.launch {
            try {
                val alreadyMember = try {
                    memberRef.get().await().exists()
                } catch (_: Exception) {
                    false
                }
                if (alreadyMember) {
                    loadProjects()
                    onResult(true)
                    return@launch
                }
                memberRef.set(
                    ProjectMemberDocument(
                        userId = user.uid,
                        displayName = user.displayName.orEmpty(),
                        role = invite.role.name,
                        inviteId = invite.inviteId
                    )
                ).await()
                loadProjects()
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectJoin", "Failed to join project", e)
                onResult(false)
            }
        }
    }

    fun changeMemberRole(
        projectId: String,
        member: ProjectMember,
        role: Role,
        onResult: (Boolean) -> Unit
    ) {
        if (member.role == Role.Admin || role == Role.Admin || member.role == role) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                Firebase.firestore.collection("projects").document(projectId)
                    .collection("members").document(member.personId)
                    .update("role", role.name)
                    .await()
                loadProjects()
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectMember", "Failed to update member role", e)
                onResult(false)
            }
        }
    }

    fun deleteMember(
        projectId: String,
        member: ProjectMember,
        onResult: (Boolean) -> Unit
    ) {
        if (member.role == Role.Admin) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                Firebase.firestore.collection("projects").document(projectId)
                    .collection("members").document(member.personId)
                    .delete()
                    .await()
                projects = projects.map { project ->
                    if (project.id == projectId) {
                        project.copy(members = project.members.filterNot { it.personId == member.personId })
                    } else {
                        project
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectMember", "Failed to delete member", e)
                onResult(false)
            }
        }
    }

    fun leaveProject(projectId: String, onResult: (Boolean) -> Unit) {
        val uid = Firebase.auth.currentUser?.uid ?: run {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                Firebase.firestore.collection("projects").document(projectId)
                    .collection("members").document(uid)
                    .delete()
                    .await()
                projects = projects.filterNot { it.id == projectId }
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectLeave", "Failed to leave project", e)
                onResult(false)
            }
        }
    }

    private suspend fun fetchProjects(uid: String): List<Project> {
        val firestore = Firebase.firestore
        val ownedProjects = firestore.collection("projects")
            .whereEqualTo("ownerId", uid)
            .get().await().documents.mapNotNull { document ->
                document.toObject(ProjectDocument::class.java)?.toProject(
                    document.id,
                    Role.Admin,
                    loadMembers(document.id)
                )
            }

        val sharedProjects = firestore.collectionGroup("members")
            .whereEqualTo("userId", uid)
            .get().await().documents.mapNotNull { membership ->
                val projectRef = membership.reference.parent.parent ?: return@mapNotNull null
                val role = Role.entries.firstOrNull { it.name == membership.getString("role") }
                    ?: return@mapNotNull null
                projectRef.get().await().toObject(ProjectDocument::class.java)?.toProject(
                    projectRef.id,
                    role,
                    loadMembers(projectRef.id)
                )
            }

        val referenceProjects = firestore.collection("projects")
            .whereEqualTo("projectId", ReferenceProjectId)
            .whereEqualTo("visibility", ProjectVisibility.Public.name)
            .get().await().documents.mapNotNull { document ->
                document.toObject(ProjectDocument::class.java)?.toProject(document.id, Role.Viewer)
            }
        return (ownedProjects + sharedProjects + referenceProjects).distinctBy(Project::id)
    }

    private suspend fun loadMembers(projectId: String): List<ProjectMember> {
        return Firebase.firestore.collection("projects").document(projectId)
            .collection("members").get().await().documents.mapNotNull { member ->
                member.toObject(ProjectMemberDocument::class.java)?.toProjectMember(member.id)
            }
    }
}

private const val ReferenceProjectId = "project-reference"
