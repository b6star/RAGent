package com.yourssu.ragent.ui.project

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectDocument
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.model.toProject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProjectViewModel : ViewModel() {
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set

    fun loadProjects() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Log.d("DEBUG", "uid: $uid")
        viewModelScope.launch {
            try {
                projects = fetchProjects(uid)
            } catch (e: Exception) {
                Log.e("ProjectLoad", "Failed to load projects", e)
            }
        }
    }

    fun createProject(project: Project, onResult: (Boolean) -> Unit) {
        val uid = Firebase.auth.currentUser?.uid ?: run {
            onResult(false)
            return
        }

        viewModelScope.launch {
            try {
                Firebase.firestore
                    .collection("projects")
                    .document(project.id)
                    .set(
                        ProjectDocument(
                            projectId = project.id,
                            name = project.name,
                            ownerId = uid,
                            githubUrl = project.githubUrl,
                            docsUrl = project.docsUrl,
                            visibility = project.visibility.name,
                            status = project.status.name
                        )
                    )
                    .await()

                projects = listOf(project) + projects
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectCreate", "Failed to create project", e)
                onResult(false)
            }
        }
    }

    fun deleteProject(project: Project, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                Firebase.firestore
                    .collection("projects")
                    .document(project.id)
                    .delete()
                    .await()

                projects = projects.filterNot { it.id == project.id }
                onResult(true)
            } catch (e: Exception) {
                Log.e("ProjectDelete", "Failed to delete project", e)
                onResult(false)
            }
        }
    }

    private suspend fun fetchProjects(uid: String): List<Project> {
        val firestore = Firebase.firestore
        val ownedProjects = firestore
            .collection("projects")
            .whereEqualTo("ownerId", uid)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(ProjectDocument::class.java)
                    ?.toProject(document.id, Role.Admin)
            }

        val sharedProjects = try {
            firestore
                .collectionGroup("members")
                .whereEqualTo("userId", uid)
                .get()
                .await()
                .documents
                .mapNotNull { membership ->
                    val projectReference = membership.reference.parent.parent
                        ?: return@mapNotNull null
                    val role = Role.entries.firstOrNull {
                        it.name == membership.getString("role")
                    } ?: return@mapNotNull null

                    projectReference.get().await()
                        .toObject(ProjectDocument::class.java)
                        ?.toProject(projectReference.id, role)
                }
        } catch (e: Exception) {
            Log.e("SharedProjectLoad", "Failed to load shared projects", e)
            emptyList()
        }

        val referenceProjects = try {
            firestore
                .collection("projects")
                .whereEqualTo("projectId", ReferenceProjectId)
                .whereEqualTo("visibility", ProjectVisibility.Public.name)
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(ProjectDocument::class.java)
                        ?.toProject(document.id, Role.Viewer)
                }
        } catch (e: Exception) {
            Log.e("ReferenceProjectLoad", "Failed to load reference project", e)
            emptyList()
        }

        return (ownedProjects + sharedProjects + referenceProjects).distinctBy(Project::id)
    }
}

private const val ReferenceProjectId = "project-reference"
