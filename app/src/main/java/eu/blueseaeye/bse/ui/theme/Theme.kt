package eu.blueseaeye.bse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A5BA8),
    onPrimary = Color.White,
    secondary = Color(0xFF33618A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD1FF),
    onPrimary = Color(0xFF00344F),
    secondary = Color(0xFFA5CAF0)
)

@Composable
fun BseTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
