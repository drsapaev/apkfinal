package com.aistudio.clinicsystem.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.aistudio.clinicsystem.BuildConfig

/**
 * A secure Jetpack Compose life-cycle helper that selectively applies
 * [WindowManager.LayoutParams.FLAG_SECURE] to the hosting Activity window when
 * active. FLAG_SECURE prevents making screenshots, video recordings, and
 * obscures background thumbnails in the Android OS app list.
 *
 * E1.5 (M0): implemented properly. Previous version was a no-op stub
 * ("Disabled FLAG_SECURE in emulator streaming environments as it causes a
 * black screen"). The stub was a security ship-blocker: PHI on screens could
 * be screenshotted.
 *
 * Usage:
 *   SecureScreen { PatientScreenContent() }
 *
 * Note: FLAG_SECURE may produce a black screen in some screencast / remote
 * display scenarios. This is the desired behavior for screens with PHI.
 * For local development screencasts, use a debug build variant and gate
 * SecureScreen behind BuildConfig.DEBUG — but production MUST leave it on.
 */
@Composable
fun SecureScreen(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val window = context.findActivity()?.window

    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    content()
}

/** Walk up the ContextWrapper chain to find the hosting Activity. */
private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
