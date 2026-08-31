package com.yourssu.ragent.ui.person

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.AiProjectTokenUsage
import com.yourssu.ragent.model.AiTokenUsage
import com.yourssu.ragent.model.AiUsageDashboard
import com.yourssu.ragent.model.Person
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker
import com.yourssu.ragent.ui.theme.DangerAccentDark
import com.yourssu.ragent.ui.theme.DangerAccentLight
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PersonDetailScreen(
    person: Person,
    profileRole: Role?,
    profileSummary: String?,
    isCurrentUser: Boolean = false,
    usageDashboard: AiUsageDashboard = AiUsageDashboard(),
    onBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    var showApiSettings by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val activeProjectIds = person.projects.mapTo(mutableSetOf()) { it.id }
    val excludedProjectUsages = if (isCurrentUser) {
        usageDashboard.projectUsages.values
            .filter { it.projectId !in activeProjectIds }
            .sortedByDescending { it.usage.totalTokens }
    } else {
        emptyList()
    }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RAGentIcon(AppIcon.Back, MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(
                Modifier
                    .padding(start = 14.dp)
                    .weight(1f)
            ) {
                Text(
                    person.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text("프로필", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isCurrentUser) {
                Surface(
                    onClick = { showApiSettings = true },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI API 설정",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InfoBlock("이름", person.name)
                if (profileRole != null && profileSummary != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Role",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                        RoleMarker(profileRole)
                    }
                    InfoBlock("Summary", profileSummary)
                }
            }
        }

        if (isCurrentUser) {
            SectionTitle("API 사용량")
            ApiUsageCard(usageDashboard)
        }

        SectionTitle(if (isCurrentUser) "내 프로젝트" else "Projects")
        if (person.projects.isEmpty() && excludedProjectUsages.isEmpty()) {
            Text(
                "표시할 프로젝트가 없습니다.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                person.projects.forEach { project ->
                    ProjectUsageCard(
                        projectName = project.name,
                        projectState = project.visibility.label,
                        usage = usageDashboard.projectUsages[project.id]
                    )
                }

                if (excludedProjectUsages.isNotEmpty()) {
                    Text(
                        "제외된 프로젝트",
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    excludedProjectUsages.forEach { usage ->
                        ProjectUsageCard(
                            projectName = usage.projectName
                                ?: "제외된 프로젝트 · ${usage.projectId.take(8)}",
                            projectState = "현재 목록에서 제외됨 · 사용량 기록 보존",
                            usage = usage
                        )
                    }
                }
            }
        }

        if (isCurrentUser) {
            DangerActionCard("로그아웃", onClick = { showLogoutConfirm = true })
        }
    }

    if (showApiSettings) {
        AiApiSettingsBottomSheet(onDismiss = { showApiSettings = false })
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("로그아웃하시겠습니까?") },
            text = { Text("현재 계정에서 로그아웃합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    }
                ) {
                    Text(
                        "로그아웃",
                        color = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun DangerActionCard(text: String, onClick: () -> Unit) {
    val accent = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            color = accent,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ApiUsageCard(dashboard: AiUsageDashboard) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            UsageLine("전체 사용량", dashboard.total)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            UsageLine("개발자 API", dashboard.developer)
            UsageLine("개인 API", dashboard.personal)

            UsageLine("서버 임베딩", dashboard.serverEmbedding)
            UsageLine("서버 검색", dashboard.serverSearch)

            EmbeddingUsageInfo(
                embedding = dashboard.serverEmbedding,
                search = dashboard.serverSearch
            )

            if (dashboard.personalModels.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "개인 API 모델별 사용량",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                dashboard.personalModels.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            model.modelName,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${formatTokens(model.usage.totalTokens)} 토큰",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageLine(label: String, usage: AiTokenUsage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "${usage.requestCount}회 요청",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Text(
            "${formatTokens(usage.totalTokens)} 토큰",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun EmbeddingUsageInfo(
    embedding: AiTokenUsage,
    search: AiTokenUsage
) {
    var showInfo by remember { mutableStateOf(false) }
    val totalTokens = embedding.totalTokens + search.totalTokens
    val chunkCount = embedding.chunkCount + search.chunkCount
    val characterCount = embedding.characterCount + search.characterCount
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Embedding 사용량", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${formatTokens(totalTokens)} 추정 토큰")
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Default.Info, contentDescription = "Embedding 계산 정보")
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Embedding 사용량 계산 정보") },
            text = {
                Text(
                    "추정 토큰: ${formatTokens(totalTokens)}\n" +
                        "Chunk 수: ${formatTokens(chunkCount)}\n" +
                        "전체 문자 수: ${formatTokens(characterCount)}\n\n" +
                        "계산 방법: 전체 문자 수 ÷ 4\n" +
                        "실제 토큰 수가 아닌 표시용 추정값입니다."
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("확인") }
            }
        )
    }
}

@Composable
private fun ProjectUsageCard(
    projectName: String,
    projectState: String,
    usage: AiProjectTokenUsage?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(projectName, fontWeight = FontWeight.Bold)
                    Text(
                        projectState,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    "${formatTokens(usage?.usage?.totalTokens ?: 0)} 토큰",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                "개인 ${formatTokens(usage?.personalTokens ?: 0)} · 개발자 ${formatTokens(usage?.developerTokens ?: 0)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatTokens(tokens: Long): String =
    NumberFormat.getIntegerInstance(Locale.KOREA).format(tokens)
