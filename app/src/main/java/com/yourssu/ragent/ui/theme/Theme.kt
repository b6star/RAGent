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
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0D1117),
    secondary = Color(0xFF8B949E),
    tertiary = Color(0xFF3DD6C6),
    background = Color(0xFF080B10),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF171D26),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF242C36),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF6E7781),
    tertiary = Color(0xFF008C8C),
    background = Color(0xFFF2F5F8),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF24292F),
    surfaceVariant = Color(0xFFE8EDF3),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE)
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
