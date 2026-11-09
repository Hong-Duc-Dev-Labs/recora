package labs.hongducdev.recora.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import labs.hongducdev.recora.utils.HapticUtil

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    )

@Composable
fun RecoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pitchBlackTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val effectiveDark = darkTheme || pitchBlackTheme
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                val dynamicScheme =
                    if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                if (effectiveDark && pitchBlackTheme) {
                    dynamicScheme.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceContainer = Color.Black,
                        surfaceContainerLowest = Color.Black,
                        surfaceContainerLow = Color.Black,
                    )
                } else {
                    dynamicScheme
                }
            }

            effectiveDark -> {
                if (pitchBlackTheme) {
                    DarkColorScheme.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceContainer = Color.Black,
                        surfaceContainerLowest = Color.Black,
                        surfaceContainerLow = Color.Black,
                    )
                } else {
                    DarkColorScheme
                }
            }

            else -> {
                LightColorScheme
            }
        }

    val context = LocalContext.current
    val gatedHaptics = HapticUtil.gate(context, LocalHapticFeedback.current)

    CompositionLocalProvider(LocalHapticFeedback provides gatedHaptics) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RecoraTypography,
            shapes = RecoraShapes,
            content = content,
        )
    }
}
