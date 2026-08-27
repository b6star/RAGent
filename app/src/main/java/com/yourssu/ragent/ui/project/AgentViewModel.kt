package com.yourssu.ragent.ui.project

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class AgentViewModel : ViewModel() {
    private val _messages = mutableStateListOf<AiChatMessage>()
    val messages: List<AiChatMessage> get() = _messages

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val functions = Firebase.functions("asia-northeast3")

    fun askQuestion(prompt: String) {
        if (prompt.isBlank()) return

        // 사용자 메시지 추가
        _messages.add(AiChatMessage(text = prompt, isUser = true))

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val data = hashMapOf("prompt" to prompt)
                val result = functions
                    .getHttpsCallable("askGemini")
                    .call(data)
                    .await()

                val resData = result.data as? Map<*, *>
                val answer = resData?.get("text") as? String ?: "응답을 처리하지 못했습니다."
                
                // AI 응답 메시지 추가
                _messages.add(AiChatMessage(text = answer, isUser = false))
            } catch (e: Exception) {
                Log.e("AgentViewModel", "AI Error", e)
                error = "오류 발생: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun clearMessages() {
        _messages.clear()
    }
}
