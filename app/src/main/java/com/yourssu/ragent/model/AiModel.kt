package com.yourssu.ragent.model

import com.yourssu.ragent.ui.agent.AiModelCatalog

enum class AiApiProvider(val displayName: String, val requestValue: String) {
    Gemini("Gemini", "gemini"),
    OpenAi("OpenAI", "openai"),
    Anthropic("Anthropic", "anthropic");

    val defaultModelId: String
        get() = when (this) {
            Gemini -> "gemini-3.5-flash-lite"
            OpenAi -> "gpt-4o"
            Anthropic -> "claude-3-5-sonnet-latest"
        }

    val models: List<AiModel>
        get() = AiModelCatalog.getModelsForProvider(this)
}

data class AiModel(val id: String, val name: String)

data class AiApiKeyState(
    val provider: AiApiProvider,
    val selectedModelId: String,
    val hasStoredKey: Boolean
)
