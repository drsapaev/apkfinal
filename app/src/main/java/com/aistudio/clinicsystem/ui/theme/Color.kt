package com.aistudio.clinicsystem.ui.theme

import androidx.compose.ui.graphics.Color

// IntellectClinic Medical Brand Colors - Light Theme
// P-01 fix: replaced MaterialTheme.colorScheme.primary (non-composable scope) with HEX.
// Previous code: `val MedicalTealLight = MaterialTheme.colorScheme.primary` — failed to
// compile because MaterialTheme.colorScheme is a @Composable extension property and cannot
// be referenced at top-level. App fell back to default Material 3 purple palette.
val MedicalTealLight = Color(0xFF4DB6AC)
val MedicalBlueLight = Color(0xFF1E88E5)
val ClinicBgLight = Color(0xFFF4F6F8)
val ClincSurfaceLight = Color(0xFFFFFFFF)

// IntellectClinic Medical Brand Colors - Dark Theme
val MedicalTealDark = Color(0xFF4DB6AC)
val MedicalBlueDark = Color(0xFF64B5F6)
val ClinicBgDark = Color(0xFF121212)
val ClinicSurfaceDark = Color(0xFF1E1E1E)
val ClinicSurfaceVariantDark = Color(0xFF2C2C2C)
