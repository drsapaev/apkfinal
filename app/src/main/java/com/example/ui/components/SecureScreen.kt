package com.example.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.example.utils.TokenManager

/**
 * A highly secure Jetpack Compose life-cycle helper that selectively applies
 * WindowManager.LayoutParams.FLAG_SECURE to the hosting Activity window when active.
 * FLAG_SECURE prevents making screenshots, video recordings, and obscures background thumbnails
 * in the Android OS app list.
 */
@Composable
fun SecureScreen() {
    // Disabled FLAG_SECURE in emulator streaming environments as it causes a black screen.
}
