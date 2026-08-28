package com.yourssu.ragent.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class AiErrorMapperTest {
    @Test
    fun `maps personal provider authentication and permission errors`() {
        assertEquals(
            AiErrorReason.API_KEY_INVALID,
            AiErrorMapper.providerReason(401)
        )
        assertEquals(
            AiErrorReason.PERMISSION_DENIED,
            AiErrorMapper.providerReason(403)
        )
    }

    @Test
    fun `distinguishes quota from temporary rate limit`() {
        assertEquals(
            AiErrorReason.QUOTA_EXCEEDED,
            AiErrorMapper.providerReason(429, "insufficient_quota")
        )
        assertEquals(
            AiErrorReason.RATE_LIMITED,
            AiErrorMapper.providerReason(429, "rate_limit_exceeded")
        )
    }

    @Test
    fun `maps model timeout content and provider failures`() {
        assertEquals(AiErrorReason.MODEL_NOT_FOUND, AiErrorMapper.providerReason(404))
        assertEquals(AiErrorReason.TIMEOUT, AiErrorMapper.providerReason(504))
        assertEquals(
            AiErrorReason.CONTENT_BLOCKED,
            AiErrorMapper.providerReason(400, "content_filter")
        )
        assertEquals(AiErrorReason.PROVIDER_UNAVAILABLE, AiErrorMapper.providerReason(503))
    }

    @Test
    fun `maps developer token limit without embedding configured amount`() {
        val error = AiErrorMapper.cloudFunctionError(
            "RESOURCE_EXHAUSTED",
            "developer_token_limit"
        )

        assertEquals(AiErrorReason.DEVELOPER_TOKEN_LIMIT, error.reason)
        assertFalse(error.message.any(Char::isDigit))
        assertFalse(error.retryable)
    }

    @Test
    fun `marks network failures as retryable`() {
        val error = AiErrorMapper.fromThrowable(UnknownHostException())

        assertEquals(AiErrorReason.NETWORK, error.reason)
        assertTrue(error.retryable)
    }
}
