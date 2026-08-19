package com.yourssu.ragent.ui.project

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.ProjectMember
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.components.RAGentIcon
import com.yourssu.ragent.ui.components.RoleMarker

@Composable
fun MembersTab(
    members: List<ProjectMember>,
    personName: (String) -> String,
    scrollIndex: Int,
    scrollOffset: Int,
    onScrollPositionChange: (Int, Int) -> Unit,
    onMemberChatClick: (ProjectMember) -> Unit,
    onMemberClick: (ProjectMember) -> Unit
) {
    val visibleMembers = members.filter { it.role != Role.Viewer }.sortedBy { if (it.role == Role.Admin) 0 else 1 }
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
            MemberRow(member, personName(member.personId), onClick = { onMemberClick(member) }, onChatClick = { onMemberChatClick(member) })
        }
    }
}

@Composable
private fun MemberRow(member: ProjectMember, name: String, onClick: () -> Unit, onChatClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RoleMarker(member.role)
                Spacer(Modifier.width(8.dp))
                Text(name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(member.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onChatClick) {
                RAGentIcon(AppIcon.ChatEmpty, MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
