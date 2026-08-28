package com.yourssu.ragent.ui.agent

import com.yourssu.ragent.model.AiApiProvider
import com.yourssu.ragent.model.AiModel

object AiModelCatalog {
    const val defaultModelName = "gemini-3.5-flash-lite"

    fun getModelsForProvider(provider: AiApiProvider): List<AiModel> {
        return when (provider) {
            AiApiProvider.Gemini -> listOf(
                AiModel("gemini-3.5-flash-lite", "gemini-3.5-flash-lite"),
                AiModel("gemini-3.7-flash", "gemini-3.7-flash"),
                AiModel("gemini-3.1-pro-preview", "gemini-3.1-pro-preview")
            )
            AiApiProvider.OpenAi -> listOf(
                AiModel("gpt-4o", "GPT-4o"),
                AiModel("gpt-4o-mini", "GPT-4o Mini")
            )
            AiApiProvider.Anthropic -> listOf(
                AiModel("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet"),
                AiModel("claude-3-5-haiku-latest", "Claude 3.5 Haiku")
            )
        }
    }
}
