package com.yourssu.ragent.data.remote

import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions

/** Single Android access point for the deployed callable-function region. */
object RAGentFunctions {
    private const val REGION = "asia-northeast3"

    val instance: FirebaseFunctions by lazy { Firebase.functions(REGION) }
}
