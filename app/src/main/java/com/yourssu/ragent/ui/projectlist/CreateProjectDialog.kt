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
import java.util.UUID
import com.yourssu.ragent.model.Project
import com.yourssu.ragent.model.ProjectVisibility
import com.yourssu.ragent.model.PublicSourceUrl
import com.yourssu.ragent.model.SourceUrlValidation
import com.yourssu.ragent.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (Project, (Boolean) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var githubUrl by remember { mutableStateOf("") }
    var docsUrl by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(ProjectVisibility.Public) }
    var isSaving by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
                "공개 GitHub Repository와 Notion 문서를 연결할 수 있습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(githubUrl, { githubUrl = it; validationError = null }, Modifier.fillMaxWidth(), label = { Text("GitHub 공개 Repository URL") }, singleLine = true, shape = RoundedCornerShape(16.dp), isError = validationError?.contains("GitHub") == true)
            OutlinedTextField(docsUrl, { docsUrl = it; validationError = null }, Modifier.fillMaxWidth(), label = { Text("Notion 공개 페이지 URL") }, singleLine = true, shape = RoundedCornerShape(16.dp), isError = validationError?.contains("Notion") == true)
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisibilityChip("Public", visibility == ProjectVisibility.Public) { visibility = ProjectVisibility.Public }
                VisibilityChip("Private", visibility == ProjectVisibility.Private) { visibility = ProjectVisibility.Private }
            }
            Button(
                enabled = name.isNotBlank() && !isSaving,
                onClick = {
                    val projectName = name.trim()
                    when (val validation = PublicSourceUrl.validate(githubUrl, docsUrl)) {
                        SourceUrlValidation.InvalidGithub -> validationError = "GitHub 공개 Repository URL을 확인해 주세요."
                        SourceUrlValidation.InvalidNotion -> validationError = "Notion 공개 페이지 URL을 확인해 주세요."
                        is SourceUrlValidation.Valid -> {
                            validationError = null
                            isSaving = true
                            onCreate(
                                Project(
                                    id = UUID.randomUUID().toString(),
                                    name = projectName,
                                    myRole = Role.Admin,
                                    githubUrl = validation.githubUrl,
                                    docsUrl = validation.notionUrl,
                                    visibility = visibility,
                                    members = emptyList()
                                )
                            ) { created ->
                                isSaving = false
                                if (created) onDismiss()
                                else validationError = "프로젝트를 저장하지 못했습니다."
                            }
                        }
                    }
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
