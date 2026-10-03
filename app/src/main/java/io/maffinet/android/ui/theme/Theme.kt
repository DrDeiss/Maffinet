package io.maffinet.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MaffinetColors.Mint,
    secondary = MaffinetColors.Muted,
    tertiary = MaffinetColors.Mint,
    background = MaffinetColors.Background,
    surface = MaffinetColors.Surface,
    surfaceVariant = MaffinetColors.SurfaceRaised,
    onPrimary = MaffinetColors.OnMint,
    onSecondary = MaffinetColors.Background,
    onBackground = MaffinetColors.Text,
    onSurface = MaffinetColors.Text,
    onSurfaceVariant = MaffinetColors.Muted,
    outline = MaffinetColors.Outline,
    error = MaffinetColors.Error
)

@Composable
fun MaffinetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
