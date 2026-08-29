package com.yourssu.ragent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSourceUrlTest {
    @Test
    fun normalizesSupportedPublicLinks() {
        val result = PublicSourceUrl.validate(
            " https://www.GitHub.com/acme/ragent.git/ ",
            "https://acme-workspace.notion.site/Project-123?pvs=4#overview"
        )

        assertTrue("unexpected result: $result", result is SourceUrlValidation.Valid)
        result as SourceUrlValidation.Valid
        assertEquals("https://github.com/acme/ragent", result.githubUrl)
        assertEquals("https://acme-workspace.notion.site/Project-123", result.notionUrl)
    }

    @Test
    fun acceptsAppNotionPageLink() {
        val result = PublicSourceUrl.validate(
            "",
            "https://app.notion.com/p/Android-LLM"
        )

        assertTrue("unexpected result: $result", result is SourceUrlValidation.Valid)
        result as SourceUrlValidation.Valid
        assertEquals("https://app.notion.com/p/Android-LLM", result.notionUrl)
    }

    @Test
    fun rejectsPrivateOrNonRepositoryLinks() {
        assertTrue(PublicSourceUrl.validate("http://github.com/acme/ragent", "").isInvalid())
        assertTrue(PublicSourceUrl.validate("https://github.com/acme/ragent/issues", "").isInvalid())
        assertTrue(PublicSourceUrl.validate("", "https://example.com/acme/page").isInvalid())
    }

    @Test
    fun permitsEmptyLinksToDisconnectSource() {
        val result = PublicSourceUrl.validate("", "")
        assertEquals(SourceUrlValidation.Valid("", ""), result)
    }

    @Test
    fun notionCaptionOnlyRemovesRecognizablePageId() {
        assertEquals("Android LLM", PublicSourceUrl.notionCaption("https://app.notion.com/p/Android-LLM-12345678123456781234567812345678"))
        assertEquals("Android 1 id", PublicSourceUrl.notionCaption("https://app.notion.com/p/Android-1-id"))
    }

    private fun SourceUrlValidation.isInvalid(): Boolean = this !is SourceUrlValidation.Valid
}
