package cyou.ttdxq.gonctool.android.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class StatusColors(
    val successContainer: Color,
    val warningContainer: Color,
    val errorContainer: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val errorText: Color,
)

private val lightStatusColors = StatusColors(
    successContainer = Color(0xFFCEEACC),
    warningContainer = Color(0xFFFFE0C4),
    errorContainer = Color(0xFFFFDAD6),
    success = Color(0xFF386A20),
    warning = Color(0xFF8B5000),
    error = Color(0xFFBA1A1A),
    errorText = Color(0xFF690005),
)

private val darkStatusColors = StatusColors(
    successContainer = Color(0xFF1F5107),
    warningContainer = Color(0xFF6D3900),
    errorContainer = Color(0xFF93000A),
    success = Color(0xFFA8D58A),
    warning = Color(0xFFFFB870),
    error = Color(0xFFFFB4AB),
    errorText = Color(0xFFFFDAD6),
)

val LocalStatusColors = compositionLocalOf { lightStatusColors }

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val typography = Typography()

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD7FC),
    onTertiaryContainer = Color(0xFF29132E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF434750),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC3C6D0),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9CC5FF),
    onPrimary = Color(0xFF002F5E),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDEBCDF),
    onTertiary = Color(0xFF402844),
    tertiaryContainer = Color(0xFF583E5C),
    onTertiaryContainer = Color(0xFFFAD7FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF434750),
    onSurfaceVariant = Color(0xFFC3C6D0),
    outline = Color(0xFF8D9099),
    outlineVariant = Color(0xFF434750),
)

@Composable
fun getStatusColors(): StatusColors = LocalStatusColors.current

@Composable
fun GoncToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val statusColors = if (darkTheme) darkStatusColors else lightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
