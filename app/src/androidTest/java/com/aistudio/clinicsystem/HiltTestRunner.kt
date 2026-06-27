package com.aistudio.clinicsystem

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Stage 4 fix: custom AndroidJUnitRunner that replaces the real
 * [ClinicSystemApplication] with [HiltTestApplication] for instrumentation tests.
 *
 * Without this, the test APK crashes on launch with:
 *   java.lang.ClassCastException: ClinicSystemApplication cannot be cast to
 *   HiltTestApplication
 * or
 *   ClassNotFoundException: didn't find class ClinicSystemApplication
 * (because the test classloader doesn't see the @HiltAndroidApp-generated
 * Hilt_ClinicSystemApplication class).
 *
 * HiltTestApplication is a minimal Application that supports @HiltAndroidTest
 * without requiring the full ClinicSystemApplication DI graph. For tests that
 * DO need the full graph (e.g. end-to-end integration tests), use
 * @HiltAndroidTest + @CustomTestApplication.
 *
 * Registered in app/build.gradle.kts:
 *   testInstrumentationRunner = "com.aistudio.clinicsystem.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application {
        // Replace the real Application class with HiltTestApplication.
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
