package com.yourssu.ragent.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val authProvider: String = "",
    val isEmailVerified: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null
) {
    fun toPerson(): Person {
        return Person(
            id = uid,
            name = displayName
        )
    }
}
