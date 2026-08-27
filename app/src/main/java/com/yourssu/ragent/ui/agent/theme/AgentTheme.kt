package com.yourssu.ragent.ui.agent.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Defines the available theme types.
 */
enum class AgentThemeType {
    DEFAULT, BLUE, GREEN, RED, PURPLE
}

/**
 * Color specifications used throughout the app.
 */
data class AgentColors(
    val isDark: Boolean,
    // Base UI
    val background: Color,
    val onBackground: Color, // General text color independent of the custom theme
    val surface: Color,
    val metadataText: Color,
    val onSurfaceVariant: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val onSurface: Color,
    // Messages
    val userBubble: Color,
    val assistantBubble: Color,
    val userText: Color,
    val assistantText: Color,
    val primary: Color,      // Heading (#), link, and button accent color
    val onPrimary: Color,    // Text color on buttons
    val emphasis: Color,     // Color for **emphasis** (separate from headings)
    val quote: Color,        // > Quote text
    val inlineCodeText: Color,
    val inlineCodeBackground: Color,
    // Code/Mermaid
    val codeBackground: Color,
    val codeBackgroundHex: String,
    val mermaidErrorHex: String,
    // Glassmorphism
    val glassBackground: Color,
    val glassBorder: Color,
    val glassContent: Color,
    val glassIconBackground: Color,
    // Other
    val attachmentRemoveIcon: Color = Color.White,
    val geminiIconTint: Color = Color.Unspecified
)

/**
 * 0. DEFAULT theme (Monochrome)
 */
val DefaultLightColors = AgentColors(
    isDark = false,
    background = Color.White,
    onBackground = Color(0xFF1A1A1B),
    surface = Color(0xFFF6F7F8),
    metadataText = Color(0xFF7C7C7C),
    onSurfaceVariant = Color(0xFF7C7C7C),
    error = Color(0xFFEA0027),
    errorContainer = Color(0xFFFFE5E9),
    onErrorContainer = Color(0xFFB3001E),
    primaryContainer = Color(0xFFEEEEEE),
    onPrimaryContainer = Color(0xFF1A1A1B),
    onSurface = Color(0xFF1A1A1B),
    userBubble = Color(0xFF1A1A1B),
    assistantBubble = Color.Transparent,
    userText = Color.White,
    assistantText = Color(0xFF1A1A1B),
    primary = Color(0xFF1A1A1B),
    onPrimary = Color.White,
    emphasis = Color.Black,
    quote = Color(0xFF666666),
    inlineCodeText = Color(0xFFD10000),
    inlineCodeBackground = Color(0xFFEEEEEE),
    codeBackground = Color(0xFFE2E8F0),
    codeBackgroundHex = "#E2E8F0",
    mermaidErrorHex = "#EA0027",
    glassBackground = Color.Black.copy(alpha = 0.88f),
    glassBorder = Color.White.copy(alpha = 0.2f),
    glassContent = Color.White,
    glassIconBackground = Color.White.copy(alpha = 0.1f)
)

val DefaultDarkColors = AgentColors(
    isDark = true,
    background = Color(0xFF030303),
    onBackground = Color(0xFFD7DADC),
    surface = Color(0xFF1A1A1B),
    metadataText = Color(0xFF818384),
    onSurfaceVariant = Color(0xFF818384),
    error = Color(0xFFFF4500),
    errorContainer = Color(0xFF341009),
    onErrorContainer = Color(0xFFFFB399),
    primaryContainer = Color(0xFF2C2C2E),
    onPrimaryContainer = Color.White,
    onSurface = Color(0xFFF0F6FC),
    userBubble = Color(0xFF3A3A3C),
    assistantBubble = Color.Transparent,
    userText = Color.White,
    assistantText = Color(0xFFF0F6FC),
    primary = Color.White,
    onPrimary = Color.Black,
    emphasis = Color.White,
    quote = Color(0xFF818384),
    inlineCodeText = Color(0xFFD93812),
    inlineCodeBackground = Color(0xFF2D2D2D),
    codeBackground = Color(0xFF1A1A1B),
    codeBackgroundHex = "#1A1A1B",
    mermaidErrorHex = "#FF4500",
    glassBackground = Color.Black.copy(alpha = 0.88f),
    glassBorder = Color.White.copy(alpha = 0.15f),
    glassContent = Color.White,
    glassIconBackground = Color.White.copy(alpha = 0.1f)
)


internal val LocalAgentColors = staticCompositionLocalOf { DefaultLightColors }

object AgentTheme {
    val colors: AgentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAgentColors.current
}

@Composable
fun AgentChatTheme(
    themeType: AgentThemeType = AgentThemeType.DEFAULT,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DefaultDarkColors else DefaultLightColors


    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primary.copy(alpha = 0.2f),
            onPrimaryContainer = colors.primary,
            secondary = colors.primary,
            onSecondary = colors.onPrimary,
            secondaryContainer = colors.primary.copy(alpha = 0.2f),
            onSecondaryContainer = colors.primary,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onBackground,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.onSurfaceVariant,
            error = colors.error,
            errorContainer = colors.errorContainer,
            onErrorContainer = colors.onErrorContainer
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primary.copy(alpha = 0.1f),
            onPrimaryContainer = colors.primary,
            secondary = colors.primary,
            onSecondary = colors.onPrimary,
            secondaryContainer = colors.primary.copy(alpha = 0.1f),
            onSecondaryContainer = colors.primary,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onBackground,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.onSurfaceVariant,
            error = colors.error,
            errorContainer = colors.errorContainer,
            onErrorContainer = colors.onErrorContainer
        )
    }

    val selectionColors = TextSelectionColors(
        handleColor = colors.primary,
        backgroundColor = colors.primary.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(
        LocalAgentColors provides colors,
        LocalTextSelectionColors provides selectionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
