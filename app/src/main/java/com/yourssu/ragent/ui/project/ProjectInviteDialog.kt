package com.yourssu.ragent.ui.project

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import com.yourssu.ragent.model.ProjectInvite
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.components.AppIcon
import com.yourssu.ragent.ui.theme.DangerAccentLight

@Composable
fun ProjectInviteDialog(
    invite: ProjectInvite,
    errorMessage: String?,
    onJoin: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "프로젝트 참여",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 프로젝트 이름 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${invite.projectName} 에 참여하시겠습니까?",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 역할 표시
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (invite.role) {
                        Role.Admin -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        Role.Member -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        Role.Viewer -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            when (invite.role) {
                                Role.Admin -> Icons.Default.AdminPanelSettings
                                Role.Member -> Icons.Default.Person
                                Role.Viewer -> Icons.Default.Visibility
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (invite.role) {
                                Role.Admin -> MaterialTheme.colorScheme.primary
                                Role.Member -> MaterialTheme.colorScheme.tertiary
                                Role.Viewer -> MaterialTheme.colorScheme.secondary
                            }
                        )
                        Text(
                            "${invite.role.name} 권한으로 참여",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = when (invite.role) {
                                Role.Admin -> MaterialTheme.colorScheme.primary
                                Role.Member -> MaterialTheme.colorScheme.tertiary
                                Role.Viewer -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }

                // 에러 메시지
                errorMessage?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onJoin,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    "참여하기",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "취소",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
