package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBg,
    primaryContainer = NeonCyan.copy(alpha = 0.1f),
    onPrimaryContainer = NeonCyan,
    secondary = ActiveTeal,
    onSecondary = ObsidianBg,
    tertiary = ActiveTeal.copy(alpha = 0.8f),
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = Color.White,
    surface = ObsidianSurface,
    onSurface = Color.White,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = DarkMuted,
    outline = BorderColor,
    inverseOnSurface = ObsidianBg,
    inverseSurface = Color.White,
    inversePrimary = NeonCyan,
    surfaceTint = NeonCyan,
    outlineVariant = BorderColor,
    scrim = Color.Black
  )

private val LightColorScheme = DarkColorScheme // Force dark theme vibe for cyber-obsidian

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true for the obsidian theme
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Disable dynamic color to maintain the brand aesthetic
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
