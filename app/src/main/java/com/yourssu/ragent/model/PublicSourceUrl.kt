package com.yourssu.ragent.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Validation and canonicalization rules for the public links supported by Phase 4. */
object PublicSourceUrl {
    const val MaxLength = 2_048

    fun normalizeGithub(raw: String): String? {
        val value = raw.trim().removeSuffix("/")
        val match = Regex(
            "^https://(?:www\\.)?github\\.com/([^/?#]+)/([^/?#]+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(value)
            ?: return if (value.isEmpty()) "" else null
        val owner = match.groupValues[1]
        val repository = match.groupValues[2].removeSuffix(".git")
        if (!isSafeSegment(owner) || !isSafeSegment(repository)) return null
        return "https://github.com/$owner/$repository"
    }

    fun normalizeNotion(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        if (value.length > MaxLength || value.any(Char::isWhitespace)) return null
        val match = Regex(
            "^https://((?:[A-Za-z0-9-]+\\.)*notion\\.site|(?:[A-Za-z0-9-]+\\.)*notion\\.so|app\\.notion\\.com)/([^?#]+?)(?:\\?[^#]*)?(?:#.*)?/?$",
            RegexOption.IGNORE_CASE
        ).matchEntire(value) ?: return null
        val path = match.groupValues[2].trim('/').trim()
        if (path.isEmpty()) return null
        val host = match.groupValues[1].lowercase()
        return "https://$host/$path"
    }

    fun validate(githubUrl: String, notionUrl: String): SourceUrlValidation {
        val github = normalizeGithub(githubUrl)
        val notion = normalizeNotion(notionUrl)
        return when {
            githubUrl.isNotBlank() && github == null -> SourceUrlValidation.InvalidGithub
            notionUrl.isNotBlank() && notion == null -> SourceUrlValidation.InvalidNotion
            else -> SourceUrlValidation.Valid(github.orEmpty(), notion.orEmpty())
        }
    }

    /** Readable caption; only a recognizable Notion page-ID suffix is removed. */
    fun notionCaption(url: String): String {
        val path = url.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/', "")
        if (path.isBlank()) return "Notion 문서"
        val decoded = runCatching { URLDecoder.decode(path, StandardCharsets.UTF_8.name()) }.getOrDefault(path)
        val withoutId = decoded
            .replace(Regex("-[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$"), "")
            .replace(Regex("-[0-9a-fA-F]{32}$"), "")
        return withoutId.replace(Regex("[-_]+"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { "Notion 문서" }
    }

    private fun isSafeSegment(value: String): Boolean =
        value.isNotEmpty() && value.length <= 100 && value.matches(Regex("[A-Za-z0-9._-]+"))
}

sealed interface SourceUrlValidation {
    data class Valid(val githubUrl: String, val notionUrl: String) : SourceUrlValidation
    data object InvalidGithub : SourceUrlValidation
    data object InvalidNotion : SourceUrlValidation
}
