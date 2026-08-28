package com.yourssu.ragent.ui.agent

import com.yourssu.ragent.model.AiApiProvider
import com.yourssu.ragent.model.AiModel





/**
 * 개발자 키는 functions/src/index.ts의 provider별 모델 목록 중 첫 번째 모델을 사용한다.
 * */

object AiModelCatalog {
    fun getModelsForProvider(provider: AiApiProvider): List<AiModel> {
        return when (provider) {
            AiApiProvider.Gemini -> listOf(
                AiModel(
                    "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite"),
                AiModel(
                    "gemini-3.5-flash", "Gemini 3.5 Flash"),
                AiModel(
                    "gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview")
            )
            AiApiProvider.OpenAi -> listOf(
                AiModel("gpt-5.6-luna", "GPT-5.6 Luna"),
                AiModel("gpt-5.6-terra", "GPT-5.6 Terra"),
                AiModel("gpt-5.6-sol", "GPT-5.6 Sol")
            )
        }
    }
}
