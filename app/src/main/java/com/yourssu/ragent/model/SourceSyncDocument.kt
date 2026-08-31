package com.yourssu.ragent.model

import com.google.firebase.Timestamp

/**
 * Read-only Android representation of
 * projects/{projectId}/sourceSync/status.
 *
 * Server protocol values remain raw strings so enforcement settings and state
 * definitions stay in the Cloud Functions source of truth.
 */
data class SourceSyncStatusDocument(
    val schemaVersion: Int = 0,
    val status: String = "",
    val activeRevisionId: String? = null,
    val lastRequestedAt: Timestamp? = null,
    val lastCheckedAt: Timestamp? = null,
    val lastChangedAt: Timestamp? = null,
    val lastCompletedAt: Timestamp? = null,
    val lastError: SourceSyncErrorDocument? = null,
    val updatedAt: Timestamp? = null
)

data class SourceSyncErrorDocument(
    val code: String = "",
    val message: String = "",
    val retryable: Boolean = false,
    val occurredAt: Timestamp? = null
)

/** Read-only Android representation of the latest RAG revision status. */
data class RagRevisionStatusDocument(
    val status: String = "",
    val chunkCount: Int = 0,
    val completedBatchCount: Int = 0,
    val totalBatchCount: Int = 0,
    val lastError: SourceSyncErrorDocument? = null,
    val updatedAt: Timestamp? = null,
    val completedAt: Timestamp? = null
)

/** Read-only Android representation of a project Source metadata document. */
data class ProjectSourceDocument(
    val schemaVersion: Int = 0,
    val sourceType: String = "",
    val canonicalUrl: String = "",
    val status: String = "",
    val manifestHash: String? = null,
    val snapshotObjectPath: String? = null,
    val sourceRevision: String? = null,
    val itemCount: Int = 0,
    val totalBytes: Long = 0,
    val manifestVersion: Int = 0,
    val extractorVersion: String = "",
    val activeRevisionId: String? = null,
    val stagingRevisionId: String? = null,
    val lastCheckedAt: Timestamp? = null,
    val lastChangedAt: Timestamp? = null,
    val lastCompletedAt: Timestamp? = null,
    val lastError: SourceSyncErrorDocument? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
