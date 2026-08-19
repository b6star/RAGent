package com.yourssu.ragent.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourssu.ragent.model.Role
import com.yourssu.ragent.ui.theme.AdminThemeColor
import com.yourssu.ragent.ui.theme.LinkMarkerColorDark
import com.yourssu.ragent.ui.theme.LinkMarkerColorLight
import com.yourssu.ragent.ui.theme.MemberThemeColor
import com.yourssu.ragent.ui.theme.ViewerThemeColor

@Composable
fun RoleMarker(role: Role, shortLabel: Boolean = false) {
    Marker(
        text = if (shortLabel) role.label.removeSuffix("자") else role.label,
        backgroundColor = role.markerColor
    )
}

@Composable
fun MemberNameMarker(name: String, role: Role) {
    Marker(text = name, backgroundColor = role.markerColor)
}

@Composable
fun MemberNameMarker(name: String, role: Role, onClick: () -> Unit) {
    Marker(text = name, backgroundColor = role.markerColor, onClick = onClick)
}

// String 확장 함수
private fun String.toDisplayText(): String {
    return this
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .removePrefix("www.")
        .trim('/')
}

private fun String.toFullUrl(): String {
    return when {
        startsWith("http://") || startsWith("https://") -> this
        startsWith("github.com") -> "https://$this"
        else -> "https://github.com/$this"
    }
}

@Composable
fun LinkMarker(
    input: String,
    icon: AppIcon? = null,
    url: String? = input.takeIf { it.isLikelyLink() },
    onClick: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val resolvedUrl = url?.toFullUrl()
    Marker(
        text = input.toDisplayText(),
        textColor = MaterialTheme.colorScheme.onSurface,
        backgroundColor = if (isSystemInDarkTheme()) LinkMarkerColorDark else LinkMarkerColorLight,
        icon = icon,
        onClick = resolvedUrl?.let {
            {
                if (onClick == null) uriHandler.openUri(it) else onClick(it)
            }
        }
    )
}

private fun String.isLikelyLink(): Boolean =
    startsWith("http://") || startsWith("https://") || startsWith("github.com") || contains("/")

private val Role.markerColor: Color
    get() = when (this) {
        Role.Admin -> AdminThemeColor
        Role.Member -> MemberThemeColor
        Role.Viewer -> ViewerThemeColor
    }

@Composable
private fun Marker(
    text: String,
    backgroundColor: Color,
    textColor: Color = backgroundColor,
    icon: AppIcon? = null,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.let { RAGentIcon(it, textColor) }
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
    if (onClick == null) {
        Surface(shape = RoundedCornerShape(8.dp), color = backgroundColor.copy(alpha = 0.14f), content = content)
    } else {
        Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = backgroundColor.copy(alpha = 0.14f), content = content)
    }
}

