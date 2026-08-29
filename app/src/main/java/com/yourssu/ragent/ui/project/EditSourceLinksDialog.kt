package com.yourssu.ragent.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.PublicSourceUrl
import com.yourssu.ragent.model.SourceUrlValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSourceLinksDialog(
    githubUrl: String,
    notionUrl: String,
    onDismiss: () -> Unit,
    onSave: (githubUrl: String, notionUrl: String, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    var github by remember(githubUrl) { mutableStateOf(githubUrl) }
    var notion by remember(notionUrl) { mutableStateOf(notionUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<SourceUrlValidation.Valid?>(null) }

    fun save(validation: SourceUrlValidation.Valid) {
        isSaving = true
        onSave(validation.githubUrl, validation.notionUrl) { saved, message ->
            isSaving = false
            if (saved) onDismiss() else error = message ?: "Source 링크를 저장하지 못했습니다."
        }
    }

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
            Text("공개 Source 연결", style = MaterialTheme.typography.headlineSmall)
            Text(
                "공개 HTTPS 링크만 연결할 수 있습니다. 비워 두면 해당 Source를 해제합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = github,
                onValueChange = { github = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub Repository URL") },
                singleLine = true,
                isError = error?.contains("GitHub") == true
            )
            OutlinedTextField(
                value = notion,
                onValueChange = { notion = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notion 공개 페이지 URL") },
                singleLine = true,
                isError = error?.contains("Notion") == true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when (val validation = PublicSourceUrl.validate(github, notion)) {
                        SourceUrlValidation.InvalidGithub -> error = "GitHub 공개 Repository URL을 확인해 주세요."
                        SourceUrlValidation.InvalidNotion -> error = "Notion 공개 페이지 URL을 확인해 주세요."
                        is SourceUrlValidation.Valid -> {
                            if (validation.githubUrl == githubUrl && validation.notionUrl == notionUrl) {
                                onDismiss()
                            } else {
                                pendingSave = validation
                            }
                        }
                    }
                }
            ) {
                Text(if (isSaving) "저장 중…" else "저장")
            }
        }
    }

    pendingSave?.let { validation ->
        AlertDialog(
            onDismissRequest = { pendingSave = null },
            title = { Text("Source 링크를 수정할까요?") },
            text = {
                Text("링크를 수정하면 서버에 저장된 RAG 정보가 삭제됩니다. 계속하시겠습니까?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSave = null
                        save(validation)
                    }
                ) {
                    Text("수정", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSave = null }) {
                    Text("취소")
                }
            }
        )
    }
}
