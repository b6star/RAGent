package com.yourssu.ragent.data.remote

import android.util.Log
import com.yourssu.ragent.model.AiApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DirectAiGenerationResult(
    val text: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val thoughtsTokens: Int,
    val totalTokens: Int,
    val modelName: String
)

class DirectAiClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun stream(
        provider: AiApiProvider,
        prompt: String,
        apiKey: String,
        model: String,
        onChunk: suspend (String) -> Unit
    ): DirectAiGenerationResult = withContext(Dispatchers.IO) {
        val request = when (provider) {
            AiApiProvider.Gemini -> geminiRequest(prompt, apiKey, model)
            AiApiProvider.OpenAi -> openAiRequest(prompt, apiKey, model)
        }

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw providerHttpException(
                    status = response.code,
                    errorBody = response.body?.string()
                )
            }

            val body = response.body ?: throw AiRequestException(AiErrorReason.EMPTY_RESPONSE)
            var text = ""
            var inputTokens = 0
            var outputTokens = 0
            var thoughtsTokens = 0
            var totalTokens = 0
            var modelName = model
            var eventType = ""

            body.charStream().buffered().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("event:") -> {
                            eventType = line.removePrefix("event:").trim()
                        }
                        line.startsWith("data:") -> {
                            val data = line.removePrefix("data:").trim()
                            if (data.isBlank()) continue
                            if (data == "[DONE]") break

                            val json = try {
                                JSONObject(data)
                            } catch (parseError: Exception) {
                                throw AiRequestException(
                                    reason = AiErrorReason.EMPTY_RESPONSE,
                                    cause = parseError
                                )
                            }
                            json.optJSONObject("error")?.let { providerError ->
                                throw providerStreamException(providerError)
                            }
                            when (provider) {
                                AiApiProvider.Gemini -> {
                                    val delta = json.optJSONArray("candidates")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("content")
                                        ?.optJSONArray("parts")
                                        ?.optJSONObject(0)
                                        ?.optString("text", "")
                                        .orEmpty()
                                    if (delta.isNotEmpty()) {
                                        text += delta
                                        onChunk(delta)
                                    }
                                    modelName = json.optString("modelVersion", model)
                                    val usage = json.optJSONObject("usageMetadata")
                                    inputTokens = usage?.optInt("promptTokenCount", 0) ?: 0
                                    outputTokens = usage?.optInt(
                                        "candidatesTokenCount",
                                        usage.optInt("responseTokenCount", 0)
                                    ) ?: 0
                                    thoughtsTokens = usage?.optInt("thoughtsTokenCount", 0) ?: 0
                                    totalTokens = usage?.optInt("totalTokenCount", 0) ?: 0
                                    totalTokens += thoughtsTokens
                                }
                                AiApiProvider.OpenAi -> {
                                    val responseType = json.optString("type", eventType)
                                    if (eventType == "error" || responseType == "error") {
                                        throw providerStreamException(json)
                                    }
                                    if (eventType == "response.failed" ||
                                        responseType == "response.failed"
                                    ) {
                                        val providerError = json.optJSONObject("response")
                                            ?.optJSONObject("error")
                                        throw providerStreamException(providerError ?: json)
                                    }
                                    if (eventType == "response.output_text.delta" ||
                                        responseType == "response.output_text.delta"
                                    ) {
                                        val delta = json.optString("delta", "")
                                        if (delta.isNotEmpty()) {
                                            text += delta
                                            onChunk(delta)
                                        }
                                    }
                                    if (eventType == "response.completed" ||
                                        responseType == "response.completed"
                                    ) {
                                        val completed = json.optJSONObject("response")
                                        val usage = completed?.optJSONObject("usage")
                                        inputTokens = usage?.optInt("input_tokens", 0) ?: 0
                                        outputTokens = usage?.optInt("output_tokens", 0) ?: 0
                                        thoughtsTokens = usage
                                            ?.optJSONObject("output_tokens_details")
                                            ?.optInt("reasoning_tokens", 0) ?: 0
                                        totalTokens = usage?.optInt("total_tokens", 0) ?: 0
                                        modelName = completed?.optString("model", model) ?: model
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (text.isBlank()) throw AiRequestException(AiErrorReason.EMPTY_RESPONSE)
            if (provider == AiApiProvider.Gemini) {
                Log.d(
                    TAG,
                    "Gemini AI answer:\n$text\n" +
                        "input token: $inputTokens\n" +
                        "output token: $outputTokens\n" +
                        "thoughts token: $thoughtsTokens"
                )
            }
            DirectAiGenerationResult(
                text = text.trim(),
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                thoughtsTokens = thoughtsTokens,
                totalTokens = totalTokens,
                modelName = modelName
            )
        }
    }

    private fun geminiRequest(prompt: String, apiKey: String, model: String): Request {
        val body = JSONObject()
            .put("contents", org.json.JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
            ))
            .toString()
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun openAiRequest(prompt: String, apiKey: String, model: String): Request {
        val body = JSONObject()
            .put("model", model)
            .put("input", prompt)
            .put("stream", true)
            .put("store", false)
            .toString()
        return Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun providerHttpException(status: Int, errorBody: String?): AiRequestException {
        val providerError = errorBody?.let { body ->
            runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        }
        val providerCode = providerError?.providerCode()
        val providerMessage = providerError?.optString("message")
        return AiRequestException(
            reason = AiErrorMapper.providerReason(status, providerCode, providerMessage),
            httpStatus = status,
            providerCode = providerCode
        )
    }

    private fun providerStreamException(error: JSONObject): AiRequestException {
        val providerCode = error.providerCode()
        val status = error.optInt(
            "http_status",
            error.optInt("status", error.optInt("code", 500))
        )
        return AiRequestException(
            reason = AiErrorMapper.providerReason(
                httpStatus = status,
                providerCode = providerCode,
                providerMessage = error.optString("message")
            ),
            providerCode = providerCode
        )
    }

    private fun JSONObject.providerCode(): String? = sequenceOf(
        optString("status"),
        optString("code"),
        optString("type")
    ).firstOrNull { it.isNotBlank() && it != "null" }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val TAG = "DirectAiClient"
    }
}
