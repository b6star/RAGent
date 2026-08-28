package com.yourssu.ragent.model

data class AiUsageRecord(
    val modelName: String,
    val keySource: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val thoughtsTokens: Long,
    val totalTokens: Long,
    val projectId: String?,
    val projectName: String?,
    val sessionId: String?,
    val sessionTitle: String?,
    val createdAt: Long
)

data class AiTokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val thoughtsTokens: Long = 0,
    val totalTokens: Long = 0,
    val requestCount: Int = 0
)

data class AiModelTokenUsage(
    val modelName: String,
    val usage: AiTokenUsage
)

data class AiProjectTokenUsage(
    val projectId: String,
    val projectName: String?,
    val usage: AiTokenUsage,
    val personalTokens: Long,
    val developerTokens: Long
)

data class AiSessionTokenUsage(
    val sessionId: String,
    val sessionTitle: String?,
    val usage: AiTokenUsage,
    val personalTokens: Long,
    val developerTokens: Long,
    val models: List<AiModelTokenUsage>,
    val lastUsageAt: Long?
)

data class AiUsageDashboard(
    val total: AiTokenUsage = AiTokenUsage(),
    val personal: AiTokenUsage = AiTokenUsage(),
    val developer: AiTokenUsage = AiTokenUsage(),
    val personalModels: List<AiModelTokenUsage> = emptyList(),
    val projectUsages: Map<String, AiProjectTokenUsage> = emptyMap(),
    val sessionUsages: Map<String, AiSessionTokenUsage> = emptyMap()
)

fun List<AiUsageRecord>.toAiUsageDashboard(): AiUsageDashboard {
    val personalRecords = filter { it.keySource == "personal" }
    val developerRecords = filter { it.keySource == "developer" }

    return AiUsageDashboard(
        total = summarizeUsage(),
        personal = personalRecords.summarizeUsage(),
        developer = developerRecords.summarizeUsage(),
        personalModels = personalRecords.groupByModel(),
        projectUsages = filter { !it.projectId.isNullOrBlank() }
            .groupBy { checkNotNull(it.projectId) }
            .mapValues { (projectId, records) ->
                AiProjectTokenUsage(
                    projectId = projectId,
                    projectName = records.latestSnapshotName(AiUsageRecord::projectName),
                    usage = records.summarizeUsage(),
                    personalTokens = records.personalTokens(),
                    developerTokens = records.developerTokens()
                )
            },
        sessionUsages = filter { !it.sessionId.isNullOrBlank() }
            .groupBy { checkNotNull(it.sessionId) }
            .mapValues { (sessionId, records) ->
                AiSessionTokenUsage(
                    sessionId = sessionId,
                    sessionTitle = records.latestSnapshotName(AiUsageRecord::sessionTitle),
                    usage = records.summarizeUsage(),
                    personalTokens = records.personalTokens(),
                    developerTokens = records.developerTokens(),
                    models = records.groupByModel(),
                    lastUsageAt = records.maxOfOrNull(AiUsageRecord::createdAt)
                )
            }
    )
}

private fun List<AiUsageRecord>.summarizeUsage() = AiTokenUsage(
    inputTokens = sumOf(AiUsageRecord::inputTokens),
    outputTokens = sumOf(AiUsageRecord::outputTokens),
    thoughtsTokens = sumOf(AiUsageRecord::thoughtsTokens),
    totalTokens = sumOf(AiUsageRecord::totalTokens),
    requestCount = size
)

private fun List<AiUsageRecord>.groupByModel(): List<AiModelTokenUsage> =
    groupBy { it.modelName.ifBlank { "Unknown model" } }
        .map { (modelName, records) ->
            AiModelTokenUsage(modelName = modelName, usage = records.summarizeUsage())
        }
        .sortedByDescending { it.usage.totalTokens }

private fun List<AiUsageRecord>.personalTokens(): Long =
    filter { it.keySource == "personal" }.sumOf(AiUsageRecord::totalTokens)

private fun List<AiUsageRecord>.developerTokens(): Long =
    filter { it.keySource == "developer" }.sumOf(AiUsageRecord::totalTokens)

private fun List<AiUsageRecord>.latestSnapshotName(
    selector: (AiUsageRecord) -> String?
): String? = asSequence()
    .sortedByDescending(AiUsageRecord::createdAt)
    .mapNotNull(selector)
    .firstOrNull { it.isNotBlank() }
