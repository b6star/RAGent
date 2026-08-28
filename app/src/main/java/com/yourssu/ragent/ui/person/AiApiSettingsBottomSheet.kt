package com.yourssu.ragent.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.data.local.AiApiKeyStorage
import com.yourssu.ragent.model.AiApiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiApiSettingsBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val storage = remember(context) { AiApiKeyStorage(context) }
    val initialState = remember(storage) { storage.getState() }
    var provider by remember { mutableStateOf(initialState.provider) }
    var apiKey by remember { mutableStateOf("") }
    var hasStoredKey by remember { mutableStateOf(initialState.hasStoredKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "AI API 설정",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Provider와 개인 API 키를 설정하세요. 키는 Firestore에 업로드하지 않고 이 기기에서 암호화해 보관합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "API Provider",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiApiProvider.entries.forEach { option ->
                    ProviderChip(
                        provider = option,
                        selected = provider == option,
                        onClick = { provider = option },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                placeholder = {
                    Text(if (hasStoredKey) "새 키를 입력하면 기존 키가 교체됩니다" else "API 키 입력")
                },
                supportingText = {
                    when {
                        errorMessage != null -> Text(errorMessage.orEmpty())
                        hasStoredKey -> Text("현재 이 기기에 API 키가 저장되어 있습니다.")
                        else -> Text("입력한 키는 화면이나 로그에 다시 표시되지 않습니다.")
                    }
                },
                isError = errorMessage != null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (isKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isKeyVisible) "API 키 숨기기" else "API 키 보기"
                        )
                    }
                }
            )

            Button(
                onClick = {
                    try {
                        storage.save(provider, apiKey)
                        apiKey = ""
                        hasStoredKey = true
                        onDismiss()
                    } catch (error: Exception) {
                        errorMessage = error.message ?: "API 키를 저장하지 못했습니다."
                    }
                },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("안전하게 저장", fontWeight = FontWeight.Bold)
            }

            if (hasStoredKey) {
                TextButton(
                    onClick = {
                        storage.clear()
                        apiKey = ""
                        hasStoredKey = false
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Text("저장된 키 삭제", modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProviderChip(
    provider: AiApiProvider,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = provider.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
        )
    }
}
