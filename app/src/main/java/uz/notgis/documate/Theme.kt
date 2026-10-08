package uz.notgis.documate

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3A8E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADFF8),
    onPrimaryContainer = Color(0xFF2A1450),
    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF17150F),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF17150F),
    surfaceVariant = Color(0xFFEFE9DD),
    onSurfaceVariant = Color(0xFF5A5446),
    outline = Color(0xFF8C8574),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9B2F0),
    onPrimary = Color(0xFF2A1450),
    primaryContainer = Color(0xFF43276E),
    onPrimaryContainer = Color(0xFFEADFF8),
    background = Color(0xFF17150F),
    onBackground = Color(0xFFF3EEE4),
    surface = Color(0xFF17150F),
    onSurface = Color(0xFFF3EEE4),
    surfaceVariant = Color(0xFF2A261D),
    onSurfaceVariant = Color(0xFFC9C2B2),
    outline = Color(0xFF8C8574),
)

@Composable
fun DocuMateTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
