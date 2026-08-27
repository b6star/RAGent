package com.yourssu.ragent.ui.navigation

import com.yourssu.ragent.model.Person
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.Role

sealed interface AppScreen {
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

data class ScrollPosition(val index: Int = 0, val offset: Int = 0)
