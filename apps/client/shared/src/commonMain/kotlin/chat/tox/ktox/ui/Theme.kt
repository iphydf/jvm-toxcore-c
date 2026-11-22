package chat.tox.ktox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light =
    lightColorScheme(
        primary = Color(0xFF2B6A8F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC9E6FF),
        onPrimaryContainer = Color(0xFF001E2F),
        secondary = Color(0xFF50606E),
        secondaryContainer = Color(0xFFD3E5F5),
        onSecondaryContainer = Color(0xFF0C1D29),
        tertiary = Color(0xFF65587B),
        background = Color(0xFFF7F9FC),
        surface = Color(0xFFF7F9FC),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F4F8),
        surfaceContainer = Color(0xFFEBEEF3),
        surfaceContainerHigh = Color(0xFFE5E8ED),
        surfaceContainerHighest = Color(0xFFDFE3E7),
    )

private val dark =
    darkColorScheme(
        primary = Color(0xFF97CCF8),
        onPrimary = Color(0xFF00344E),
        primaryContainer = Color(0xFF0B4D6F),
        onPrimaryContainer = Color(0xFFC9E6FF),
        secondary = Color(0xFFB7C9D9),
        secondaryContainer = Color(0xFF384956),
        onSecondaryContainer = Color(0xFFD3E5F5),
        tertiary = Color(0xFFCFBFE8),
        background = Color(0xFF0F1418),
        surface = Color(0xFF0F1418),
        surfaceContainerLowest = Color(0xFF0A0F12),
        surfaceContainerLow = Color(0xFF171C20),
        surfaceContainer = Color(0xFF1B2024),
        surfaceContainerHigh = Color(0xFF262A2F),
        surfaceContainerHighest = Color(0xFF30353A),
    )

/** kTox's colours, or the platform's (Android's wallpaper colours) when it has some. */
@Composable
fun KToxTheme(
    platformColors: ((dark: Boolean) -> ColorScheme)? = null,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = platformColors?.invoke(isDark) ?: if (isDark) dark else light,
        content = content,
    )
}

/** Online green, the same in both themes. */
val Online = Color(0xFF3DBB5D)
val Away = Color(0xFFF2B33D)
val Busy = Color(0xFFE5484D)
