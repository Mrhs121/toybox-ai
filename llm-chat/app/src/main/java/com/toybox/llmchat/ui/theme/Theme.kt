package com.toybox.llmchat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7EB8DA),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B71),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFFB4CAD8),
    onSecondary = Color(0xFF1F3340),
    secondaryContainer = Color(0xFF364957),
    onSecondaryContainer = Color(0xFFD0E6F4),
    surface = Color(0xFF0E1113),
    onSurface = Color(0xFFDEE3E6),
    surfaceVariant = Color(0xFF2A2F33),
    onSurfaceVariant = Color(0xFFBFC8CE),
    surfaceContainer = Color(0xFF161A1D),
    surfaceContainerLow = Color(0xFF111517),
    surfaceContainerHigh = Color(0xFF1E2225),
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFDEE3E6),
    outline = Color(0xFF40484E),
    outlineVariant = Color(0xFF2A2F33)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006782),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001F2B),
    secondary = Color(0xFF4B626C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEEAF6),
    onSecondaryContainer = Color(0xFF061E27),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE4E9),
    onSurfaceVariant = Color(0xFF40484E),
    surfaceContainer = Color(0xFFEFF2F4),
    surfaceContainerLow = Color(0xFFF5F8FA),
    surfaceContainerHigh = Color(0xFFE8ECF0),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1E),
    outline = Color(0xFF70787E),
    outlineVariant = Color(0xFFC0C8CE)
)

@Composable
fun LLMChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
