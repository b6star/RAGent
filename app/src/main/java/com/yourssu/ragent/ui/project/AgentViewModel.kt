package com.yourssu.ragent.ui.project

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val lastMessage: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val totalTokens: Int? = null,
    val responseTimeMs: Long? = null
)

class AgentViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val functions = Firebase.functions("asia-northeast3")

    private val _sessions = mutableStateListOf<AiChatSession>()
    val sessions: List<AiChatSession> get() = _sessions

    private val _messages = mutableStateMapOf<String, SnapshotStateList<AiChatMessage>>()
    
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    
    var currentSessionId by mutableStateOf<String?>(null)
        private set

    fun getMessagesForSession(sessionId: String): List<AiChatMessage> {
        return _messages[sessionId] ?: emptyList()
    }

    /**
     * 프로젝트별 대화 세션 목록 로드
     */
    fun loadSessions(projectId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Real-time listener로 변경하여 즉각 반영되도록 함
                db.collection("users").document(uid)
                    .collection("ai_chats").document(projectId)
                    .collection("sessions")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) return@addSnapshotListener
                        
                        _sessions.clear()
                        snapshot?.documents?.forEach { doc ->
                            _sessions.add(AiChatSession(
                                id = doc.id,
                                projectId = projectId,
                                title = doc.getString("title") ?: "Untitled",
                                lastMessage = doc.getString("lastMessage") ?: "",
                                updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                            ))
                        }
                    }
            } catch (e: Exception) {
                Log.e("AgentVM", "Load sessions error", e)
            }
        }
    }

    /**
     * 특정 세션의 메시지 로드
     */
    fun loadMessages(projectId: String, sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (_messages.containsKey(sessionId)) return

        val msgList = mutableStateListOf<AiChatMessage>()
        _messages[sessionId] = msgList

        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .collection("ai_chats").document(projectId)
                    .collection("sessions").document(sessionId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) return@addSnapshotListener
                        
                        msgList.clear()
                        snapshot?.documents?.forEach { doc ->
                            msgList.add(AiChatMessage(
                                id = doc.id,
                                sessionId = sessionId,
                                text = doc.getString("text") ?: "",
                                isUser = doc.getBoolean("isUser") ?: true,
                                timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                                modelName = doc.getString("modelName"),
                                totalTokens = doc.getLong("totalTokens")?.toInt()
                            ))
                        }
                    }
            } catch (e: Exception) {
                Log.e("AgentVM", "Load messages error", e)
            }
        }
    }

    /**
     * 새로운 대화 세션 생성 (Firestore 직접 저장)
     */
    fun startNewSession(projectId: String, title: String, onCreated: (AiChatSession) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val sessionId = UUID.randomUUID().toString()
        val newSession = AiChatSession(id = sessionId, projectId = projectId, title = title)

        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .collection("ai_chats").document(projectId)
                    .collection("sessions").document(sessionId)
                    .set(mapOf(
                        "title" to title,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "sessionTotalTokens" to 0
                    )).await()
                
                currentSessionId = sessionId
                _messages[sessionId] = mutableStateListOf()
                onCreated(newSession)
            } catch (e: Exception) {
                Log.e("AgentVM", "Create session error", e)
            }
        }
    }

    fun selectSession(projectId: String, sessionId: String) {
        currentSessionId = sessionId
        loadMessages(projectId, sessionId)
    }

    fun renameSession(projectId: String, sessionId: String, newTitle: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .collection("ai_chats").document(projectId)
                    .collection("sessions").document(sessionId)
                    .update("title", newTitle).await()
            } catch (e: Exception) {
                Log.e("AgentVM", "Rename session error", e)
            }
        }
    }

    fun deleteSession(projectId: String, sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val sessionRef = db.collection("users").document(uid)
                    .collection("ai_chats").document(projectId)
                    .collection("sessions").document(sessionId)
                
                // Sub-collection messages also need to be deleted
                val messages = sessionRef.collection("messages").get().await()
                db.runBatch { batch ->
                    messages.documents.forEach { batch.delete(it.reference) }
                    batch.delete(sessionRef)
                }.await()
                
                if (currentSessionId == sessionId) {
                    currentSessionId = null
                }
            } catch (e: Exception) {
                Log.e("AgentVM", "Delete session error", e)
            }
        }
    }

    /**
     * 하이브리드 질문 로직: 메시지 저장은 앱에서, AI 호출은 서버에서
     */
    fun askQuestion(projectId: String, prompt: String) {
        val sessionId = currentSessionId ?: return
        if (prompt.isBlank()) return
        val uid = auth.currentUser?.uid ?: return

        val sessionRef = db.collection("users").document(uid)
            .collection("ai_chats").document(projectId)
            .collection("sessions").document(sessionId)

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                // 1. 첫 메시지인지 확인하여 제목 업데이트 준비
                val messagesSnapshot = sessionRef.collection("messages").get().await()
                val isFirstMessage = messagesSnapshot.isEmpty

                // 2. 사용자 질문 Firestore에 즉시 저장
                sessionRef.collection("messages").add(mapOf(
                    "text" to prompt,
                    "isUser" to true,
                    "timestamp" to FieldValue.serverTimestamp()
                )).await()

                if (isFirstMessage) {
                    val autoTitle = if (prompt.length > 25) prompt.take(22) + "..." else prompt
                    sessionRef.update("title", autoTitle).await()
                }

                // 3. Cloud Functions 호출
                val result = functions
                    .getHttpsCallable("askGemini")
                    .call(hashMapOf("prompt" to prompt))
                    .await()

                val resData = result.data as? Map<*, *>
                val answer = resData?.get("text") as? String ?: ""
                val tokens = (resData?.get("totalTokens") as? Number)?.toInt() ?: 0
                val modelName = resData?.get("modelName") as? String

                // 4. AI 답변 Firestore에 저장
                sessionRef.collection("messages").add(mapOf(
                    "text" to answer,
                    "isUser" to false,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "modelName" to modelName,
                    "totalTokens" to tokens
                )).await()

                // 5. 세션 메타데이터 업데이트
                sessionRef.update(mapOf(
                    "lastMessage" to answer.take(100),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "sessionTotalTokens" to FieldValue.increment(tokens.toLong())
                )).await()

            } catch (e: Exception) {
                Log.e("AgentViewModel", "AI Error", e)
                error = "오류 발생: ${e.localizedMessage ?: "알 수 없는 에러"}"
            } finally {
                isLoading = false
            }
        }
    }
}
