package com.yourssu.ragent.ui.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.yourssu.ragent.model.PublicSourceUrl
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
    onCreateInvite: (Role, Boolean) -> Unit,
    onSourceLinksChange: (String, String, (Boolean, String?) -> Unit) -> Unit,
    onProjectVisibilityChange: (ProjectVisibility, (Boolean) -> Unit) -> Unit,
    onDeleteProject: () -> Unit,
    onLeaveProject: () -> Unit
) {
    var projectVisibility by remember(project.id, project.visibility) { mutableStateOf(project.visibility) }
    var myVisibility by remember(project.id) { mutableStateOf(project.visibility) }
    var isVisibilitySaving by remember { mutableStateOf(false) }
    var showPr by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var regenerateRole by remember { mutableStateOf<Role?>(null) }
    var showSourceEditor by remember { mutableStateOf(false) }
    val canRead = projectVisibility == ProjectVisibility.Public || project.myRole != Role.Viewer

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "프로젝트 정보",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )

        InfoRow("프로젝트 이름", project.name)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GitHub", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LinkMarker(project.githubUrl.ifBlank { "연결 없음" }, icon = AppIcon.Github)
            InfoRow("Notion 문서", project.docsUrl.ifBlank { "연결 없음" })
            if (project.myRole == Role.Admin) {
                TextButton(onClick = { showSourceEditor = true }) {
                    Text("GitHub · Notion 링크 수정")
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("나의 역할", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            RoleMarker(project.myRole, false)
        }
        MemberMarkers(project, personName, onMemberClick)
        VisibilityRow(
            label = "프로젝트 공개",
            enabled = project.myRole == Role.Admin && !isVisibilitySaving,
            checked = projectVisibility == ProjectVisibility.Public,
            onCheckedChange = {
                val previousVisibility = projectVisibility
                val previousMyVisibility = myVisibility
                val newVisibility = if (it) ProjectVisibility.Public else ProjectVisibility.Private
                projectVisibility = newVisibility
                isVisibilitySaving = true
                if (newVisibility == ProjectVisibility.Private) myVisibility = ProjectVisibility.Private
                onProjectVisibilityChange(newVisibility) { updated ->
                    if (!updated) {
                        projectVisibility = previousVisibility
                        myVisibility = previousMyVisibility
                    }
                    isVisibilitySaving = false
                }
            }
        )
        VisibilityRow(
            label = "내 열람 설정",
            enabled = projectVisibility == ProjectVisibility.Public && !isVisibilitySaving,
            checked = myVisibility == ProjectVisibility.Public,
            onCheckedChange = { myVisibility = if (it) ProjectVisibility.Public else ProjectVisibility.Private }
        )
        InfoRow("읽기 권한", if (canRead) "관리자/팀원/열람자 가능" else "관리자/팀원만 가능")
        InfoRow("동기화", "Mock 상태")
        if (project.myRole == Role.Admin) {
            InviteLinkSection(
                onShare = { onCreateInvite(it, false) },
                onRegenerate = { regenerateRole = it }
            )
        }
        LatestPrCard(project.latestPullRequest, personName, onClick = { showPr = true })
        when (project.myRole) {
            Role.Admin -> DangerActionCard("프로젝트 삭제하기", onClick = { showDeleteConfirm = true })
            Role.Member, Role.Viewer -> DangerActionCard("프로젝트 나가기", onClick = { showLeaveConfirm = true })
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
            text = "프로젝트와 멤버, 초대 링크, 댓글 데이터가 영구적으로 삭제됩니다.",
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

    regenerateRole?.let { role ->
        val roleLabel = if (role == Role.Member) "팀원" else "열람자"
        AlertDialog(
            onDismissRequest = { regenerateRole = null },
            title = { Text("$roleLabel 링크를 재발급할까요?") },
            text = { Text("기존 $roleLabel 초대 링크는 즉시 만료됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        regenerateRole = null
                        onCreateInvite(role, true)
                    }
                ) {
                    Text("재발급")
                }
            },
            dismissButton = {
                TextButton(onClick = { regenerateRole = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (showSourceEditor) {
        EditSourceLinksDialog(
            githubUrl = project.githubUrl,
            notionUrl = project.docsUrl,
            onDismiss = { showSourceEditor = false },
            onSave = { github, notion, onResult ->
                onSourceLinksChange(github, notion) { saved, message ->
                    onResult(saved, message)
                    if (saved) showSourceEditor = false
                }
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
                items = project.members,
                key = { it.id }
            ) { member ->
                MemberNameMarker(
                    member.name.ifBlank { personName(member.personId) },
                    member.role,
                    onClick = { onMemberClick(member) }
                )
            }
        }
    }
}

@Composable
private fun InviteLinkSection(
    onShare: (Role) -> Unit,
    onRegenerate: (Role) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "초대 링크",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column {
                InviteLinkRow("팀원", Role.Member, onShare, onRegenerate)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InviteLinkRow("열람자", Role.Viewer, onShare, onRegenerate)
            }
        }
    }
}

@Composable
private fun InviteLinkRow(
    label: String,
    role: Role,
    onShare: (Role) -> Unit,
    onRegenerate: (Role) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$label 링크", fontWeight = FontWeight.SemiBold)
            RoleMarker(role, true)
        }
        TextButton(onClick = { onRegenerate(role) }) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("재발급")
        }
        IconButton(onClick = { onShare(role) }) {
            Icon(Icons.Default.Share, contentDescription = "$label 초대 링크 공유")
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
        if (value.startsWith("https://") && value.contains("notion.", ignoreCase = true)) {
            LinkMarker(
                input = PublicSourceUrl.notionCaption(value),
                icon = AppIcon.Notion,
                url = value
            )
        } else {
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
