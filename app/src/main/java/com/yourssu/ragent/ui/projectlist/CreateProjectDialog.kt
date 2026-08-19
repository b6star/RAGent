package com.yourssu.ragent.ui.projectlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (Project) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var githubUrl by remember { mutableStateOf("") }
    var docsUrl by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(ProjectVisibility.Public) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("새 프로젝트", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Phase 1에서는 로컬 Mock으로 추가합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(githubUrl, { githubUrl = it }, Modifier.fillMaxWidth(), label = { Text("GitHub URL") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(docsUrl, { docsUrl = it }, Modifier.fillMaxWidth(), label = { Text("Docs / Notion URL") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisibilityChip("Public", visibility == ProjectVisibility.Public) { visibility = ProjectVisibility.Public }
                VisibilityChip("Private", visibility == ProjectVisibility.Private) { visibility = ProjectVisibility.Private }
            }
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val projectName = name.trim()
                    onCreate(
                        Project(
                            id = "project-${projectName.lowercase().replace(" ", "-")}",
                            name = projectName,
                            myRole = Role.Admin,
                            githubUrl = githubUrl.trim(),
                            docsUrl = docsUrl.trim(),
                            visibility = visibility
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("프로젝트 만들기", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VisibilityChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
        )
    }
}
