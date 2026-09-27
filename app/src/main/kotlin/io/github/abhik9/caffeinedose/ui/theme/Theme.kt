package io.github.abhik9.caffeinedose.ui.theme

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.abhik9.caffeinedose.settings.ThemeMode

/**
 * Fallback palette (when dynamic colors are unavailable or disabled): roasted coffee, generated from the `#7B4F2C` seed
 * color with the Material 3 tonal spot scheme.
 */
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF895020),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC4),
    onPrimaryContainer = Color(0xFF6D3A09),
    secondary = Color(0xFF745945),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC4),
    onSecondaryContainer = Color(0xFF5B412F),
    tertiary = Color(0xFF5D6136),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E7AF),
    onTertiaryContainer = Color(0xFF464A20),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DFD2),
    onSurfaceVariant = Color(0xFF52443B),
    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD6C3B7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E9),
    surfaceContainer = Color(0xFFFBEBE1),
    surfaceContainerHigh = Color(0xFFF5E5DC),
    surfaceContainerHighest = Color(0xFFF0DFD6),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB781),
    onPrimary = Color(0xFF4E2600),
    primaryContainer = Color(0xFF6D3A09),
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = Color(0xFFE4BFA7),
    onSecondary = Color(0xFF422B1A),
    secondaryContainer = Color(0xFF5B412F),
    onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = Color(0xFFC6CA95),
    onTertiary = Color(0xFF2F330C),
    tertiaryContainer = Color(0xFF464A20),
    onTertiaryContainer = Color(0xFFE3E7AF),
    background = Color(0xFF19120D),
    onBackground = Color(0xFFF0DFD6),
    surface = Color(0xFF19120D),
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD6C3B7),
    outline = Color(0xFF9F8D82),
    outlineVariant = Color(0xFF52443B),
    surfaceContainerLowest = Color(0xFF140D08),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainer = Color(0xFF261E18),
    surfaceContainerHigh = Color(0xFF312822),
    surfaceContainerHighest = Color(0xFF3C332D),
)

@Composable
fun CaffeineDoseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && SDK_INT >= S -> LocalContext.current.let { if (dark) dynamicDarkColorScheme(it) else dynamicLightColorScheme(it) }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
