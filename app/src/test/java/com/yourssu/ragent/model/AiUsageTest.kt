package com.yourssu.ragent.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AiUsageTest {
    @Test
    fun aggregatesUsageByKeySourceModelProjectAndSession() {
        val dashboard = listOf(
            usage(
                modelName = "gemini-flash-lite",
                keySource = "personal",
                totalTokens = 15,
                projectId = "project-1",
                projectName = "Active Project",
                sessionId = "session-1"
            ),
            usage(
                modelName = "gemini-flash-lite",
                keySource = "developer",
                totalTokens = 20,
                projectId = "project-1",
                projectName = "Renamed Project",
                sessionId = "session-1"
            ),
            usage(
                modelName = "gpt-luna",
                keySource = "personal",
                totalTokens = 30,
                projectId = "project-2",
                projectName = "Deleted Project",
                sessionId = "session-2"
            )
        ).toAiUsageDashboard()

        assertEquals(65, dashboard.total.totalTokens)
        assertEquals(45, dashboard.personal.totalTokens)
        assertEquals(20, dashboard.developer.totalTokens)
        assertEquals(30, dashboard.personalModels.first().usage.totalTokens)
        assertEquals(35, dashboard.projectUsages.getValue("project-1").usage.totalTokens)
        assertEquals(15, dashboard.projectUsages.getValue("project-1").personalTokens)
        assertEquals(20, dashboard.projectUsages.getValue("project-1").developerTokens)
        assertEquals("Renamed Project", dashboard.projectUsages.getValue("project-1").projectName)
        assertEquals("Deleted Project", dashboard.projectUsages.getValue("project-2").projectName)
        assertEquals(35, dashboard.sessionUsages.getValue("session-1").usage.totalTokens)
        assertEquals(1, dashboard.sessionUsages.getValue("session-1").models.size)
    }

    @Test
    fun keepsUsageWhenProjectSnapshotIsMissing() {
        val dashboard = listOf(
            usage(
                modelName = "gemini-flash-lite",
                keySource = "personal",
                totalTokens = 42,
                projectId = "deleted-project",
                projectName = null,
                sessionId = "deleted-session"
            )
        ).toAiUsageDashboard()

        assertEquals(42, dashboard.total.totalTokens)
        assertEquals(42, dashboard.projectUsages.getValue("deleted-project").usage.totalTokens)
        assertEquals(null, dashboard.projectUsages.getValue("deleted-project").projectName)
        assertEquals(42, dashboard.sessionUsages.getValue("deleted-session").usage.totalTokens)
    }

    private fun usage(
        modelName: String,
        keySource: String,
        totalTokens: Long,
        projectId: String,
        projectName: String?,
        sessionId: String
    ) = AiUsageRecord(
        modelName = modelName,
        keySource = keySource,
        inputTokens = totalTokens,
        outputTokens = 0,
        thoughtsTokens = 0,
        totalTokens = totalTokens,
        projectId = projectId,
        projectName = projectName,
        sessionId = sessionId,
        sessionTitle = "Session $sessionId",
        createdAt = totalTokens
    )
}
