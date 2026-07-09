package com.aistudio.clinicsystem.ui.screens

import androidx.compose.runtime.Composable
import com.aistudio.clinicsystem.ui.viewmodel.ClinicViewModel

/**
 * P0-3 audit fix: release-safe stub for [SyncConsoleView].
 *
 * The real `SyncConsoleView` (741 LOC debug console with sync log viewer,
 * pending-sync inspector, force-sync buttons) lives in
 * `app/src/debug/java/com/aistudio/clinicsystem/ui/screens/SyncConsoleView.kt`.
 *
 * In a debug build, the Gradle `debug` source-set overrides this stub —
 * Kotlin resolves the more specific debug source-set file. The full debug
 * console is compiled into debug APKs and provides all the developer-facing
 * sync inspection features.
 *
 * In a release build, the debug source-set is NOT compiled, so this stub
 * is the only `SyncConsoleView` definition. It is a no-op — `MainActivity`
 * gates the call site with `if (BuildConfig.DEBUG)`, so the stub is never
 * actually composed in a release build, but the Kotlin compiler needs the
 * symbol to exist at compile time (R8 may eliminate the dead branch, but
 * Kotlin compilation happens before R8).
 *
 * Without this stub, `MainActivity.kt:20` (`import
 * com.aistudio.clinicsystem.ui.screens.SyncConsoleView`) would fail to
 * resolve in a release build, breaking the release artifact. The CI
 * workflow `release-smoke` previously used `continue-on-error: true` to
 * mask this failure — meaning the release APK was never actually verified
 * to compile.
 *
 * This stub is also a defensive guard: if a future refactor accidentally
 * removes the `BuildConfig.DEBUG` gate from the call site, the release
 * build will still compile (but the no-op stub will be composed,
 * producing no UI).
 *
 * See audit finding P0-3 in `docs/AUDIT_2026-07-10.md`.
 */
@Composable
fun SyncConsoleView(viewModel: ClinicViewModel) {
    // No-op in release builds. The debug source-set provides the real
    // implementation; this stub exists solely so that MainActivity.kt
    // compiles in release builds (where the debug source-set is excluded).
    //
    // The call site in MainActivity is gated by `if (BuildConfig.DEBUG)`,
    // so this composable is never actually invoked in release builds.
}
