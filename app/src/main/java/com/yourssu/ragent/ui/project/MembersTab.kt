package com.yourssu.ragent.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker

@Composable
fun MembersTab(
    members: List<ProjectMember>,
    personName: (String) -> String,
    canManageMembers: Boolean,
    scrollIndex: Int,
    scrollOffset: Int,
    onScrollPositionChange: (Int, Int) -> Unit,
    onMemberChatClick: (ProjectMember) -> Unit,
    onMemberClick: (ProjectMember) -> Unit,
    onRoleChange: (ProjectMember, Role) -> Unit,
    onMemberDelete: (ProjectMember) -> Unit
) {
    val visibleMembers = members.sortedBy { it.role.ordinal }
    val listState = rememberLazyListState()

    LaunchedEffect(visibleMembers.size) {
        if (visibleMembers.isNotEmpty()) {
            listState.scrollToItem(scrollIndex.coerceAtMost(visibleMembers.lastIndex), scrollOffset)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollPositionChange(index, offset) }
    }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(visibleMembers, key = { it.id }) { member ->
            MemberRow(
                member = member,
                name = member.name.ifBlank { personName(member.personId) },
                canManage = canManageMembers,
                onClick = { onMemberClick(member) },
                onChatClick = { onMemberChatClick(member) },
                onRoleChange = { onRoleChange(member, it) },
                onDelete = { onMemberDelete(member) }
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: ProjectMember,
    name: String,
    canManage: Boolean,
    onClick: () -> Unit,
    onChatClick: () -> Unit,
    onRoleChange: (Role) -> Unit,
    onDelete: () -> Unit
) {
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(onClick = onClick, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RoleMarker(member.role)
                Spacer(Modifier.width(8.dp))
                if (member.id == Firebase.auth.uid) {
                    Text("$name (나)", fontWeight = FontWeight.Bold)
                } else {
                    Text(name, fontWeight = FontWeight.Bold)
                }
                if (member.summary.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(member.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (canManage && member.role != Role.Admin) {
                Box {
                    IconButton(onClick = { roleMenuExpanded = true }) {
                        RAGentIcon(AppIcon.More, MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        val nextRole = if (member.role == Role.Member) Role.Viewer else Role.Member
                        DropdownMenuItem(
                            text = { Text(if (nextRole == Role.Member) "팀원으로 변경" else "열람자로 변경") },
                            onClick = {
                                roleMenuExpanded = false
                                onRoleChange(nextRole)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("멤버 삭제", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                roleMenuExpanded = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onChatClick) {
                RAGentIcon(AppIcon.ChatEmpty, MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("$name 님을 삭제할까요?") },
            text = { Text("프로젝트 멤버 목록과 접근 권한에서 제외됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}
