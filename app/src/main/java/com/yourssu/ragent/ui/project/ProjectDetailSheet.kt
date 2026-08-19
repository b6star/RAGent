package com.yourssu.ragent.ui.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.PullRequest
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.LinkMarker
import com.yourssu.ragent.ui.components.MemberNameMarker
import com.yourssu.ragent.ui.components.RoleMarker
import com.yourssu.ragent.ui.theme.DangerAccentDark
import com.yourssu.ragent.ui.theme.DangerAccentLight
import com.yourssu.ragent.ui.theme.PrAccentDark
import com.yourssu.ragent.ui.theme.PrAccentLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailsSheet(
    project: Project,
    personName: (String) -> String,
    onMemberClick: (ProjectMember) -> Unit,
    onDeleteProject: () -> Unit,
    onLeaveProject: () -> Unit
) {
    var projectVisibility by remember(project.name) { mutableStateOf(project.visibility) }
    var myVisibility by remember(project.name) { mutableStateOf(project.visibility) }
    var showPr by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val canRead = projectVisibility == ProjectVisibility.Public || project.myRole != Role.Viewer

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("프로젝트 정보", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        InfoRow("프로젝트 이름", project.name)
        Column {
            Text("GitHub", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LinkMarker(project.githubUrl.ifBlank { "연결 없음" }, icon = AppIcon.Github)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("나의 역할", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            RoleMarker(project.myRole, false)
        }
        InfoRow("Docs", project.docsUrl.ifBlank { "연결 없음" })
        MemberMarkers(project, personName, onMemberClick)
        VisibilityRow(
            label = "프로젝트 공개",
            enabled = project.myRole == Role.Admin,
            checked = projectVisibility == ProjectVisibility.Public,
            onCheckedChange = {
                projectVisibility = if (it) ProjectVisibility.Public else ProjectVisibility.Private
                if (projectVisibility == ProjectVisibility.Private) myVisibility = ProjectVisibility.Private
            }
        )
        VisibilityRow(
            label = "내 열람 설정",
            enabled = projectVisibility == ProjectVisibility.Public,
            checked = myVisibility == ProjectVisibility.Public,
            onCheckedChange = { myVisibility = if (it) ProjectVisibility.Public else ProjectVisibility.Private }
        )
        InfoRow("읽기 권한", if (canRead) "관리자/팀원/열람자 가능" else "관리자/팀원만 가능")
        InfoRow("동기화", "Mock 상태")
        LatestPrCard(project.latestPullRequest, personName, onClick = { showPr = true })
        when (project.myRole) {
            Role.Admin -> DangerActionCard("프로젝트 삭제하기", onClick = { showDeleteConfirm = true })
            Role.Member -> DangerActionCard("프로젝트 나가기", onClick = { showLeaveConfirm = true })
            Role.Viewer -> Unit
        }
    }

    if (showPr) {
        ModalBottomSheet(
            onDismissRequest = { showPr = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PullRequestSheet(project.latestPullRequest, personName)
        }
    }

    if (showDeleteConfirm) {
        ProjectActionDialog(
            title = "정말 ${project.name}을 삭제하시겠습니까?",
            text = "프로젝트를 삭제하면 관련된 데이터(대화내용, 멤버연동 정보)가 삭제됩니다. 삭제된 프로젝트는 서버의 휴지통으로 이동하며 30일 후에 완전히 삭제됩니다.",
            confirmText = "삭제",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDeleteProject()
            }
        )
    }

    if (showLeaveConfirm) {
        ProjectActionDialog(
            title = "정말 ${project.name}에서 나가시겠습니까?",
            text = "프로젝트에서 나가면 Members에서 제외되며 프로젝트 열람 권한이 사라질 수 있습니다.",
            confirmText = "나가기",
            onDismiss = { showLeaveConfirm = false },
            onConfirm = {
                showLeaveConfirm = false
                onLeaveProject()
            }
        )
    }
}

@Composable
private fun ProjectActionDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val danger = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = danger, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun MemberMarkers(project: Project, personName: (String) -> String, onMemberClick: (ProjectMember) -> Unit) {
    Column {
        Text("멤버", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        )  {
            items(
                items = project.members.filter { it.role != Role.Viewer },
                key = { it.id }
            ) { member ->
                MemberNameMarker(
                    personName(member.personId),
                    member.role,
                    onClick = { onMemberClick(member) }
                )
            }
        }
    }
}

@Composable
private fun LatestPrCard(pr: PullRequest, personName: (String) -> String, onClick: () -> Unit) {
    val accent = if (isSystemInDarkTheme()) PrAccentDark else PrAccentLight
    AccentCard(accent = accent, onClick = onClick) {
        Text("Latest PR", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(pr.branchName, color = accent, fontWeight = FontWeight.Black)
            Text("#${pr.number} by ${personName(pr.author.personId)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DangerActionCard(text: String, onClick: () -> Unit) {
    val accent = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight
    AccentCard(accent = accent, onClick = onClick) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccentCard(accent: Color, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content
        )
    }
}

@Composable
private fun VisibilityRow(label: String, enabled: Boolean, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(if (checked) "Public" else "Private", style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PullRequestSheet(pr: PullRequest, personName: (String) -> String) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("PR 상세", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        InfoRow("Title", pr.title)
        InfoRow("Branch", pr.branchName)
        InfoRow("Author", personName(pr.author.personId))
        InfoRow("Updated", pr.updatedAt.toString())
        InfoRow("Number", "#${pr.number}")
        Column {
            Text("URL", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LinkMarker(pr.url.ifBlank { "연결 없음" }, icon = AppIcon.Github)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
