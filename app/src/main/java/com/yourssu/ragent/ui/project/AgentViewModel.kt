package com.yourssu.ragent.ui.project

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.StreamResponse
import com.google.firebase.functions.functions
import com.yourssu.ragent.data.local.AiApiKeyStorage
import com.yourssu.ragent.data.remote.AiErrorMapper
import com.yourssu.ragent.data.remote.AiErrorReason
import com.yourssu.ragent.data.remote.AiRequestException
import com.yourssu.ragent.data.remote.DirectAiClient
import com.yourssu.ragent.model.AiApiProvider
import com.yourssu.ragent.model.AiUsageDashboard
import com.yourssu.ragent.model.AiUsageRecord
import com.yourssu.ragent.model.toAiUsageDashboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val lastMessage: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class AiSelectionDraft(
    val projectId: String,
    val sourceUrl: String,
    val kind: AiSelectionKind,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val thoughtsTokens: Int? = null,
    val totalTokens: Int? = null,
    val keySource: String? = null,
    val responseTimeMs: Long? = null
)

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val functions = Firebase.functions("asia-northeast3")
    private val directAiClient = DirectAiClient()
    val aiApiKeyStorage = AiApiKeyStorage(application)

    private val _sessions = mutableStateListOf<AiChatSession>()
    val sessions: List<AiChatSession> get() = _sessions

    private val _messages = mutableStateMapOf<String, SnapshotStateList<AiChatMessage>>()
    private val sessionListeners = mutableMapOf<String, ListenerRegistration>()
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()
    private val streamingDrafts = mutableMapOf<String, AiChatMessage>()
    private var usageListener: ListenerRegistration? = null
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        observeAiUsage(firebaseAuth.currentUser?.uid)
    }
    
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    
    var currentSessionId by mutableStateOf<String?>(null)
        private set
    var pendingSelection by mutableStateOf<AiSelectionDraft?>(null)
        private set

    fun updatePendingSelection(selection: AiSelectionDraft) {
        pendingSelection = selection
    }

    fun clearPendingSelection() {
        pendingSelection = null
    }

    var selectedModelId by mutableStateOf<String?>(null)
        private set

    var usageDashboard by mutableStateOf(AiUsageDashboard())
        private set

    init {
        selectedModelId = aiApiKeyStorage.getState().selectedModelId
        auth.addAuthStateListener(authStateListener)
    }

    private fun observeAiUsage(uid: String?) {
        usageListener?.remove()
        usageListener = null

        if (uid == null) {
            usageDashboard = AiUsageDashboard()
            return
        }

        usageListener = db.collection("users").document(uid)
            .collection("ai_usage")
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    Log.e("AgentVM", "AI usage listener error", listenerError)
                    return@addSnapshotListener
                }

                val records = snapshot?.documents.orEmpty().map { document ->
                    AiUsageRecord(
                        modelName = document.getString("modelName") ?: "Unknown model",
                        keySource = document.getString("keySource") ?: "unknown",
                        inputTokens = document.getLong("inputTokens") ?: 0,
                        outputTokens = document.getLong("outputTokens") ?: 0,
                        thoughtsTokens = document.getLong("thoughtsTokens") ?: 0,
                        totalTokens = document.getLong("totalTokens") ?: 0,
                        projectId = document.getString("projectId"),
                        projectName = document.getString("projectName"),
                        sessionId = document.getString("sessionId"),
                        sessionTitle = document.getString("sessionTitle"),
                        createdAt = document.getTimestamp("createdAt")?.toDate()?.time ?: 0
                    )
                }
                usageDashboard = records.toAiUsageDashboard()
            }
    }

    fun updateSelectedModel(modelId: String) {
        selectedModelId = modelId
        aiApiKeyStorage.updateModel(modelId)
    }

    fun getMessagesForSession(sessionId: String): List<AiChatMessage> {
        return _messages[sessionId] ?: emptyList()
    }

    /**
     * 프로젝트별 대화 세션 목록 로드
     */
    fun loadSessions(projectId: String) {
        val uid = auth.currentUser?.uid ?: return
        if (sessionListeners.containsKey(projectId)) return

        val registration = db.collection("users").document(uid)
            .collection("ai_chats").document(projectId)
            .collection("sessions")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    Log.e("AgentVM", "Session listener error", listenerError)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val nextSessions = runCatching {
                    snapshot.documents.map { doc ->
                        AiChatSession(
                            id = doc.id,
                            projectId = projectId,
                            title = doc.getString("title") ?: "Untitled",
                            lastMessage = doc.getString("lastMessage") ?: "",
                            updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                        )
                    }
                }.getOrElse { conversionError ->
                    Log.e("AgentVM", "Session conversion error", conversionError)
                    return@addSnapshotListener
                }

                Snapshot.withMutableSnapshot {
                    _sessions.removeAll { it.projectId == projectId }
                    _sessions.addAll(nextSessions)
                }
            }
        sessionListeners[projectId] = registration
    }

    /**
     * 특정 세션의 메시지 로드
     */
    fun loadMessages(projectId: String, sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        val msgList = _messages.getOrPut(sessionId) { mutableStateListOf() }
        if (messageListeners.containsKey(sessionId)) return

        val registration = db.collection("users").document(uid)
            .collection("ai_chats").document(projectId)
            .collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    Log.e("AgentVM", "Message listener error", listenerError)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val nextMessages = runCatching {
                    snapshot.documents.map { doc ->
                        AiChatMessage(
                            id = doc.id,
                            sessionId = sessionId,
                            text = doc.getString("text") ?: "",
                            isUser = doc.getBoolean("isUser") ?: true,
                            timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                            modelName = doc.getString("modelName"),
                            inputTokens = doc.getLong("inputTokens")?.toInt(),
                            outputTokens = doc.getLong("outputTokens")?.toInt(),
                            thoughtsTokens = doc.getLong("thoughtsTokens")?.toInt(),
                            totalTokens = doc.getLong("totalTokens")?.toInt(),
                            keySource = doc.getString("keySource"),
                            responseTimeMs = doc.getLong("responseTimeMs")
                        )
                    }
                }.getOrElse { conversionError ->
                    Log.e("AgentVM", "Message conversion error", conversionError)
                    return@addSnapshotListener
                }

                Snapshot.withMutableSnapshot {
                    msgList.clear()
                    msgList.addAll(nextMessages)
                    streamingDrafts[sessionId]
                        ?.takeUnless { draft ->
                            nextMessages.any { message -> message.id == draft.id }
                        }
                        ?.let(msgList::add)
                }
            }
        messageListeners[sessionId] = registration
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
                loadMessages(projectId, sessionId)
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

                messageListeners.remove(sessionId)?.remove()
                _messages.remove(sessionId)
                
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
                val apiSettings = aiApiKeyStorage.getState()
                val personalApiKey = if (apiSettings.hasStoredKey) {
                    aiApiKeyStorage.readApiKey(apiSettings.provider)
                        ?: throw AiRequestException(AiErrorReason.API_KEY_INVALID)
                } else {
                    null
                }

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

                // 3. 개인 키는 Android에서 직접 호출하고, 없으면 개발자 키를 Cloud Function에서 사용
                val model = if (personalApiKey != null) {
                    selectedModelId ?: apiSettings.selectedModelId
                } else {
                    // 개발자 키는 Cloud Function의 provider별 첫 번째 모델만 사용한다.
                    apiSettings.provider.defaultModelId
                }
                val assistantMessageRef = sessionRef.collection("messages").document()
                val responseStartedAt = System.currentTimeMillis()
                streamingDrafts[sessionId] = AiChatMessage(
                    id = assistantMessageRef.id,
                    sessionId = sessionId,
                    text = "",
                    isUser = false,
                    timestamp = responseStartedAt
                )
                updateStreamingDraft(sessionId) { it }

                var answer: String
                var inputTokens: Int
                var outputTokens: Int
                var thoughtsTokens: Int
                var tokens: Int
                var modelName: String?
                val keySource: String
                var usageSyncWarning: String? = null

                if (personalApiKey != null) {
                    val result = directAiClient.stream(
                        provider = apiSettings.provider,
                        prompt = prompt,
                        apiKey = personalApiKey,
                        model = model
                    ) { delta ->
                        updateStreamingDraft(sessionId) { draft ->
                            draft.copy(text = draft.text + delta)
                        }
                    }
                    answer = result.text
                    inputTokens = result.inputTokens
                    outputTokens = result.outputTokens
                    thoughtsTokens = result.thoughtsTokens
                    tokens = result.totalTokens
                    modelName = result.modelName
                    keySource = "personal"

                    val usageData = hashMapOf<String, Any>(
                        "usageId" to assistantMessageRef.id,
                        "provider" to apiSettings.provider.requestValue,
                        "modelName" to result.modelName,
                        "inputTokens" to result.inputTokens,
                        "outputTokens" to result.outputTokens,
                        "thoughtsTokens" to result.thoughtsTokens,
                        "totalTokens" to result.totalTokens,
                        "projectId" to projectId,
                        "sessionId" to sessionId
                    )
                    runCatching {
                        recordPersonalAiUsageWithRetry(usageData)
                    }.onFailure { usageError ->
                        val mappedError = AiErrorMapper.fromThrowable(usageError)
                        Log.e(
                            TAG,
                            "Personal usage sync failed: reason=${mappedError.reason}, " +
                                "retryable=${mappedError.retryable}, " +
                                "type=${usageError.javaClass.simpleName}"
                        )
                        usageSyncWarning =
                            "답변은 저장됐지만 사용량 통계는 서버에 반영되지 않았습니다. " +
                            mappedError.message
                    }
                } else {
                    val requestData = hashMapOf<String, Any>(
                        "prompt" to prompt,
                        "provider" to apiSettings.provider.requestValue,
                        "model" to model,
                        "usageId" to assistantMessageRef.id,
                        "projectId" to projectId,
                        "sessionId" to sessionId
                    )
                    var finalResult: Map<*, *>? = null
                    functions.getHttpsCallable("askAi")
                        .stream(requestData)
                        .asFlow()
                        .collect { streamResponse ->
                            when (streamResponse) {
                                is StreamResponse.Message -> {
                                    val chunk = streamResponse.message.data as? Map<*, *>
                                    if (chunk?.get("type") == "error") {
                                        val reason = chunk["reason"] as? String
                                        if (reason == "developer_token_limit") {
                                            throw AiRequestException(
                                                AiErrorReason.DEVELOPER_TOKEN_LIMIT
                                            )
                                        }
                                    } else if (chunk?.get("type") == "text-delta") {
                                        val delta = chunk["delta"] as? String ?: ""
                                        if (delta.isNotEmpty()) {
                                            updateStreamingDraft(sessionId) { draft ->
                                                draft.copy(text = draft.text + delta)
                                            }
                                        }
                                    }
                                }
                                is StreamResponse.Result -> {
                                    finalResult = streamResponse.result.data as? Map<*, *>
                                }
                            }
                        }

                    val resData = finalResult
                        ?: throw AiRequestException(AiErrorReason.EMPTY_RESPONSE)
                    answer = (resData["text"] as? String)
                        ?.takeIf(String::isNotBlank)
                        ?: throw AiRequestException(AiErrorReason.EMPTY_RESPONSE)
                    inputTokens = (resData["inputTokens"] as? Number)?.toInt() ?: 0
                    outputTokens = (resData["outputTokens"] as? Number)?.toInt() ?: 0
                    thoughtsTokens = (resData["thoughtsTokens"] as? Number)?.toInt() ?: 0
                    tokens = (resData["totalTokens"] as? Number)?.toInt() ?: 0
                    modelName = resData["modelName"] as? String
                    keySource = "developer"
                }
                val responseTimeMs = System.currentTimeMillis() - responseStartedAt

                updateStreamingDraft(sessionId) { draft ->
                    draft.copy(
                        text = answer,
                        modelName = modelName,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        thoughtsTokens = thoughtsTokens,
                        totalTokens = tokens,
                        keySource = keySource,
                        responseTimeMs = responseTimeMs
                    )
                }

                // 4. AI 답변 Firestore에 저장
                assistantMessageRef.set(mapOf(
                    "text" to answer,
                    "isUser" to false,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "modelName" to modelName,
                    "inputTokens" to inputTokens,
                    "outputTokens" to outputTokens,
                    "thoughtsTokens" to thoughtsTokens,
                    "totalTokens" to tokens,
                    "keySource" to keySource,
                    "responseTimeMs" to responseTimeMs
                )).await()
                streamingDrafts.remove(sessionId)

                // 5. 세션 메타데이터 업데이트
                sessionRef.update(mapOf(
                    "lastMessage" to answer.take(100),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "sessionTotalTokens" to FieldValue.increment(tokens.toLong())
                )).await()

                error = usageSyncWarning

            } catch (e: CancellationException) {
                removeStreamingDraft(sessionId)
                throw e
            } catch (e: Exception) {
                removeStreamingDraft(sessionId)
                val mappedError = AiErrorMapper.fromThrowable(e)
                Log.e(
                    TAG,
                    "AI request failed: reason=${mappedError.reason}, " +
                        "retryable=${mappedError.retryable}, " +
                        "type=${e.javaClass.simpleName}"
                )
                error = mappedError.message
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun recordPersonalAiUsageWithRetry(data: HashMap<String, Any>) {
        var lastError: Throwable? = null
        repeat(PERSONAL_USAGE_SYNC_ATTEMPTS) { attempt ->
            try {
                functions.getHttpsCallable("recordPersonalAiUsage")
                    .call(data)
                    .await()
                return
            } catch (error: Exception) {
                lastError = error
                val mappedError = AiErrorMapper.fromThrowable(error)
                if (!mappedError.retryable || attempt == PERSONAL_USAGE_SYNC_ATTEMPTS - 1) {
                    throw error
                }
                delay(PERSONAL_USAGE_RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Personal usage sync failed")
    }

    private fun updateStreamingDraft(
        sessionId: String,
        update: (AiChatMessage) -> AiChatMessage
    ) {
        val current = streamingDrafts[sessionId] ?: return
        val updated = update(current)
        streamingDrafts[sessionId] = updated
        val messageList = _messages.getOrPut(sessionId) { mutableStateListOf() }
        Snapshot.withMutableSnapshot {
            val index = messageList.indexOfFirst { it.id == updated.id }
            if (index >= 0) messageList[index] = updated else messageList.add(updated)
        }
    }

    private fun removeStreamingDraft(sessionId: String) {
        val draft = streamingDrafts.remove(sessionId) ?: return
        Snapshot.withMutableSnapshot {
            _messages[sessionId]?.removeAll { it.id == draft.id }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        usageListener?.remove()
        sessionListeners.values.forEach(ListenerRegistration::remove)
        messageListeners.values.forEach(ListenerRegistration::remove)
        sessionListeners.clear()
        messageListeners.clear()
        super.onCleared()
    }

    private companion object {
        const val TAG = "AgentViewModel"
        const val PERSONAL_USAGE_SYNC_ATTEMPTS = 3
        const val PERSONAL_USAGE_RETRY_BASE_DELAY_MS = 500L
    }
}
