package com.yourssu.ragent

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.yourssu.ragent.model.UserProfile
import com.yourssu.ragent.ui.RAGentApp
import com.yourssu.ragent.ui.auth.LoginScreen
import com.yourssu.ragent.ui.theme.RAGentTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val auth = Firebase.auth
            var user by remember { mutableStateOf(auth.currentUser) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            RAGentTheme {
                /*
                if (BuildConfig.ENABLE_ANONYMOUS_LOGIN && user == null) {
                    LaunchedEffect(Unit) {
                        user = signInAnonymously()
                        if (user == null) {
                            errorMessage = "익명 로그인에 실패했습니다."
                        }
                    }
                }
                 */
                if (user == null) {
                    LoginScreen(
                        errorMessage = errorMessage,
                        onGoogleLoginClick = {
                            scope.launch {
                                val loginError = signInWithGoogle()
                                if (loginError != null) {
                                    errorMessage = loginError
                                    return@launch
                                }

                                val signedInUser = auth.currentUser
                                if (signedInUser == null) {
                                    errorMessage = "로그인 사용자 정보를 확인하지 못했습니다."
                                    return@launch
                                }

                                val saveError = saveUser(signedInUser)
                                errorMessage = saveError
                                if (saveError == null) {
                                    user = signedInUser
                                }
                            }
                        }
                    )
                } else {
                    RAGentApp()
                }
            }
        }
    }

    private suspend fun signInAnonymously(): FirebaseUser? {
        return try {
            Firebase.auth.signInAnonymously().await().user
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveUser(user: FirebaseUser): String? {
        return try {
            val profile = UserProfile(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName ?: "익명의 사용자",
                photoUrl = user.photoUrl?.toString().orEmpty(),
                authProvider = user.providerData
                    .firstOrNull { it.providerId != "firebase" }
                    ?.providerId
                    .orEmpty(),
                isEmailVerified = user.isEmailVerified
            )

            val userRef = Firebase.firestore
                .collection("users")
                .document(user.uid)

            val snapshot = userRef.get().await()
            val commonData = mapOf(
                "uid" to profile.uid,
                "email" to profile.email,
                "displayName" to profile.displayName,
                "photoUrl" to profile.photoUrl,
                "authProvider" to profile.authProvider,
                "isEmailVerified" to profile.isEmailVerified,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastLoginAt" to FieldValue.serverTimestamp()
            )

            if (snapshot.exists()) {
                userRef.update(commonData).await()
            } else {
                userRef.set(
                    commonData + ("createdAt" to FieldValue.serverTimestamp())
                ).await()
            }

            null
        } catch (e: Exception) {
            Log.e("SaveUser", "에러 발생: ${e.message}", e)
            "사용자 정보 저장에 실패했습니다."
        }
    }

    private suspend fun signInWithGoogle(): String? {
        return try {
            val result = try {
                getGoogleCredentialResult(useFallbackButtonFlow = false)
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                if ("28439" in message || "User disabled the feature" in message) {
                    getGoogleCredentialResult(useFallbackButtonFlow = true)
                } else {
                    throw e
                }
            }

            val credential = result.credential

            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleIdTokenCredential.idToken,
                    null
                )

                Firebase.auth.signInWithCredential(firebaseCredential).await()
                null
            } else {
                "Google 로그인 정보를 가져오지 못했습니다."
            }
        } catch (e: Exception) {
            e.message ?: "Google 로그인에 실패했습니다."
        }
    }

    private suspend fun getGoogleCredentialResult(
        useFallbackButtonFlow: Boolean
    ): GetCredentialResponse {
        val clientId = getString(R.string.default_web_client_id)

        val option = if (useFallbackButtonFlow) {
            GetSignInWithGoogleOption.Builder(clientId).build()
        } else {
            GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false)
                .build()
        }

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return CredentialManager.create(this).getCredential(
            context = this,
            request = request
        )
    }

}
