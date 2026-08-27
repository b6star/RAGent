package com.yourssu.ragent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.yourssu.ragent.model.Role

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF081C15),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenOnContainerDark,
    secondary = GreenSecondaryDark,
    onSecondary = Color(0xFF081C15),
    secondaryContainer = Color(0xFF2D6A4F),
    onSecondaryContainer = Color(0xFFD8F3DC),
    tertiary = GreenTertiaryDark,
    background = Color(0xFF080B10),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF141E1A),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF1B2A24),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF2D6A4F)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = GreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7E4C7),
    onSecondaryContainer = Color(0xFF081C15),
    tertiary = GreenTertiary,
    background = Color(0xFFF2F5F8),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF24292F),
    surfaceVariant = Color(0xFFE9F5EE),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFB7E4C7)
)

@Composable
fun RAGentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
