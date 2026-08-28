package com.yourssu.ragent.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class AiErrorReason {
    API_KEY_INVALID,
    PERMISSION_DENIED,
    QUOTA_EXCEEDED,
    RATE_LIMITED,
    MODEL_NOT_FOUND,
    INVALID_REQUEST,
    CONTENT_BLOCKED,
    TIMEOUT,
    NETWORK,
    PROVIDER_UNAVAILABLE,
    EMPTY_RESPONSE,
    DEVELOPER_TOKEN_LIMIT,
    DEVELOPER_CONFIGURATION,
    USAGE_SYNC_FAILED,
    AUTH_REQUIRED,
    DATA_ACCESS_DENIED,
    UNKNOWN
}

data class AiUserError(
    val reason: AiErrorReason,
    val message: String,
    val retryable: Boolean
)

class AiRequestException(
    val reason: AiErrorReason,
    val httpStatus: Int? = null,
    val providerCode: String? = null,
    cause: Throwable? = null
) : IOException("AI request failed: ${reason.name}", cause)

object AiErrorMapper {
    fun providerReason(
        httpStatus: Int,
        providerCode: String? = null,
        providerMessage: String? = null
    ): AiErrorReason {
        val hint = listOfNotNull(providerCode, providerMessage)
            .joinToString(" ")
            .lowercase()

        return when {
            hint.containsAny(
                "invalid_api_key",
                "api key not valid",
                "api_key_invalid",
                "authentication"
            ) -> AiErrorReason.API_KEY_INVALID
            hint.containsAny("content_filter", "content policy", "safety", "blocked", "bio_policy") ->
                AiErrorReason.CONTENT_BLOCKED
            hint.containsAny("model_not_found", "model not found") ->
                AiErrorReason.MODEL_NOT_FOUND
            hint.containsAny(
                "insufficient_quota",
                "quota_exceeded",
                "daily quota",
                "billing",
                "credit",
                "spend limit"
            ) -> AiErrorReason.QUOTA_EXCEEDED
            hint.contains("rate_limit_exceeded") -> AiErrorReason.RATE_LIMITED
            hint.containsAny(
                "invalid_request",
                "invalid_argument",
                "invalid_prompt",
                "invalid prompt"
            ) ->
                AiErrorReason.INVALID_REQUEST
            httpStatus == 401 -> AiErrorReason.API_KEY_INVALID
            httpStatus == 403 -> AiErrorReason.PERMISSION_DENIED
            httpStatus == 404 -> AiErrorReason.MODEL_NOT_FOUND
            httpStatus == 408 || httpStatus == 504 -> AiErrorReason.TIMEOUT
            httpStatus == 429 -> AiErrorReason.RATE_LIMITED
            httpStatus == 409 -> AiErrorReason.PROVIDER_UNAVAILABLE
            httpStatus == 400 || httpStatus == 422 ->
                AiErrorReason.INVALID_REQUEST
            httpStatus >= 500 -> AiErrorReason.PROVIDER_UNAVAILABLE
            else -> AiErrorReason.UNKNOWN
        }
    }

    fun fromThrowable(error: Throwable): AiUserError {
        val causes = buildList {
            var current: Throwable? = error
            repeat(8) {
                val cause = current ?: return@repeat
                add(cause)
                current = cause.cause
            }
        }

        causes.filterIsInstance<AiRequestException>().firstOrNull()?.let {
            return personalApiError(it.reason)
        }
        causes.filterIsInstance<FirebaseFunctionsException>().firstOrNull()?.let {
            return cloudFunctionError(it.code.name, detailReason(it.details))
        }
        causes.filterIsInstance<SocketTimeoutException>().firstOrNull()?.let {
            return userError(AiErrorReason.TIMEOUT)
        }
        if (causes.any {
                it is FirebaseNetworkException ||
                    it is UnknownHostException ||
                    it is ConnectException
            }
        ) {
            return userError(AiErrorReason.NETWORK)
        }
        causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()?.let {
            return firestoreError(it.code)
        }
        if (causes.any { it is IOException }) {
            return userError(AiErrorReason.NETWORK)
        }
        return userError(AiErrorReason.UNKNOWN)
    }

    fun cloudFunctionError(
        codeName: String,
        detailReason: String? = null
    ): AiUserError {
        cloudReason(detailReason)?.let { return userError(it) }

        val reason = when (codeName) {
            "UNAUTHENTICATED" -> AiErrorReason.AUTH_REQUIRED
            "PERMISSION_DENIED" -> AiErrorReason.DATA_ACCESS_DENIED
            "RESOURCE_EXHAUSTED" -> AiErrorReason.QUOTA_EXCEEDED
            "INVALID_ARGUMENT", "FAILED_PRECONDITION" -> AiErrorReason.INVALID_REQUEST
            "NOT_FOUND" -> AiErrorReason.MODEL_NOT_FOUND
            "DEADLINE_EXCEEDED" -> AiErrorReason.TIMEOUT
            "UNAVAILABLE", "INTERNAL" -> AiErrorReason.PROVIDER_UNAVAILABLE
            else -> AiErrorReason.UNKNOWN
        }
        return userError(reason)
    }

    private fun firestoreError(code: FirebaseFirestoreException.Code): AiUserError {
        val reason = when (code.name) {
            "UNAUTHENTICATED" -> AiErrorReason.AUTH_REQUIRED
            "PERMISSION_DENIED" -> AiErrorReason.DATA_ACCESS_DENIED
            "DEADLINE_EXCEEDED" -> AiErrorReason.TIMEOUT
            "UNAVAILABLE" -> AiErrorReason.NETWORK
            "RESOURCE_EXHAUSTED" -> AiErrorReason.QUOTA_EXCEEDED
            else -> AiErrorReason.UNKNOWN
        }
        return userError(reason)
    }

    private fun personalApiError(reason: AiErrorReason): AiUserError = when (reason) {
        AiErrorReason.API_KEY_INVALID -> AiUserError(
            reason,
            "개인 API 키가 유효하지 않습니다. 프로필의 AI API 설정에서 키를 확인해 주세요.",
            false
        )
        AiErrorReason.PERMISSION_DENIED -> AiUserError(
            reason,
            "개인 API 키에 선택한 모델을 사용할 권한이 없습니다.",
            false
        )
        AiErrorReason.QUOTA_EXCEEDED -> AiUserError(
            reason,
            "개인 API 키의 사용량 또는 결제 한도를 초과했습니다. Provider 콘솔을 확인해 주세요.",
            false
        )
        else -> userError(reason)
    }

    private fun cloudReason(value: String?): AiErrorReason? = when (value) {
        "developer_token_limit" -> AiErrorReason.DEVELOPER_TOKEN_LIMIT
        "developer_api_key_invalid", "developer_permission_denied" ->
            AiErrorReason.DEVELOPER_CONFIGURATION
        "provider_quota_exceeded" -> AiErrorReason.QUOTA_EXCEEDED
        "rate_limited" -> AiErrorReason.RATE_LIMITED
        "model_not_found" -> AiErrorReason.MODEL_NOT_FOUND
        "invalid_request" -> AiErrorReason.INVALID_REQUEST
        "content_blocked" -> AiErrorReason.CONTENT_BLOCKED
        "timeout" -> AiErrorReason.TIMEOUT
        "provider_unavailable" -> AiErrorReason.PROVIDER_UNAVAILABLE
        "empty_response" -> AiErrorReason.EMPTY_RESPONSE
        "usage_sync_failed" -> AiErrorReason.USAGE_SYNC_FAILED
        "developer_key_missing" -> AiErrorReason.DEVELOPER_CONFIGURATION
        else -> null
    }

    private fun detailReason(details: Any?): String? = when (details) {
        is Map<*, *> -> details["reason"] as? String
        is JSONObject -> details.optString("reason").takeIf(String::isNotBlank)
        else -> null
    }

    private fun userError(reason: AiErrorReason): AiUserError = when (reason) {
        AiErrorReason.API_KEY_INVALID -> AiUserError(reason, "API 키가 유효하지 않습니다.", false)
        AiErrorReason.PERMISSION_DENIED -> AiUserError(
            reason,
            "선택한 모델을 사용할 권한이 없습니다.",
            false
        )
        AiErrorReason.QUOTA_EXCEEDED -> AiUserError(
            reason,
            "API 사용량 또는 결제 한도를 초과했습니다.",
            false
        )
        AiErrorReason.RATE_LIMITED -> AiUserError(
            reason,
            "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
            true
        )
        AiErrorReason.MODEL_NOT_FOUND -> AiUserError(
            reason,
            "선택한 모델을 찾을 수 없거나 현재 사용할 수 없습니다.",
            false
        )
        AiErrorReason.INVALID_REQUEST -> AiUserError(
            reason,
            "AI 요청 형식이나 모델 설정을 확인해 주세요.",
            false
        )
        AiErrorReason.CONTENT_BLOCKED -> AiUserError(
            reason,
            "안전 정책으로 인해 이 요청의 답변을 생성할 수 없습니다.",
            false
        )
        AiErrorReason.TIMEOUT -> AiUserError(
            reason,
            "AI 응답 시간이 초과됐습니다. 잠시 후 다시 시도해 주세요.",
            true
        )
        AiErrorReason.NETWORK -> AiUserError(
            reason,
            "네트워크 연결을 확인한 뒤 다시 시도해 주세요.",
            true
        )
        AiErrorReason.PROVIDER_UNAVAILABLE -> AiUserError(
            reason,
            "AI 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.",
            true
        )
        AiErrorReason.EMPTY_RESPONSE -> AiUserError(
            reason,
            "AI가 표시할 수 있는 답변을 반환하지 않았습니다. 질문을 바꿔 다시 시도해 주세요.",
            true
        )
        AiErrorReason.DEVELOPER_TOKEN_LIMIT -> AiUserError(
            reason,
            "개발자 API 무료 토큰을 모두 사용했습니다. 개인 API 키를 설정해 주세요.",
            false
        )
        AiErrorReason.DEVELOPER_CONFIGURATION -> AiUserError(
            reason,
            "개발자 API 설정에 문제가 있습니다. 개인 API 키를 사용하거나 관리자에게 문의해 주세요.",
            false
        )
        AiErrorReason.USAGE_SYNC_FAILED -> AiUserError(
            reason,
            "AI 사용량을 서버에 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
            true
        )
        AiErrorReason.AUTH_REQUIRED -> AiUserError(
            reason,
            "로그인 정보가 만료됐습니다. 다시 로그인해 주세요.",
            false
        )
        AiErrorReason.DATA_ACCESS_DENIED -> AiUserError(
            reason,
            "대화 데이터를 저장할 권한이 없습니다. 로그인 상태를 확인해 주세요.",
            false
        )
        AiErrorReason.UNKNOWN -> AiUserError(
            reason,
            "AI 응답을 처리하는 중 알 수 없는 오류가 발생했습니다.",
            false
        )
    }

    private fun String.containsAny(vararg values: String): Boolean =
        values.any(::contains)
}
