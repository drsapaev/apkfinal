package com.aistudio.clinicsystem.ui.theme

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
    primary = MedicalTealDark,
    onPrimary = Color(0xFF003730),
    // P-01 fix: explicit HEX instead of MaterialTheme.colorScheme.* (non-composable scope)
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFB3ECE3),
    secondary = MedicalBlueDark,
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFE3F2FD),
    background = ClinicBgDark,
    surface = ClinicSurfaceDark,
    surfaceVariant = ClinicSurfaceVariantDark,
    onBackground = Color(0xFFE3E3E3),
    onSurface = Color(0xFFE3E3E3)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MedicalTealLight,
    // P-01 fix: explicit HEX instead of MaterialTheme.colorScheme.* (non-composable scope)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3ECE3),
    onPrimaryContainer = Color(0xFF005048),
    secondary = MedicalBlueLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF0D47A1),
    background = ClinicBgLight,
    surface = ClincSurfaceLight,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic colors by default to preserve the premium medical color palette
  dynamicColor: Boolean = false,
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
