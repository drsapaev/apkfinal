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
    val context = LocalContext.current
    val isSecureEnabled = TokenManager.isScreenSecureEnabled(context)

    DisposableEffect(isSecureEnabled) {
        val activity = context as? Activity
        val window = activity?.window
        if (isSecureEnabled && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        
        onDispose {
            // When leaving the screen, always ensure the flag is cleared so subsequent
            // non-sensitive screens (like Login or a general home screen) are not blocked.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
