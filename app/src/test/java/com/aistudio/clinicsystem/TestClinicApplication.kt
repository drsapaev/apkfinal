package com.aistudio.clinicsystem

import android.app.Application

/**
 * Robolectric-only Application (wired globally via robolectric.properties).
 *
 * The real [ClinicSystemApplication] is @HiltAndroidApp: bootstrapping it in
 * a JVM test injects [com.aistudio.clinicsystem.data.db.ClinicDatabase],
 * whose E1.6 fail-closed guard throws because EncryptedSharedPreferences /
 * AndroidKeyStore do not exist under Robolectric. That crashed EVERY
 * Robolectric suite at bootstrap (144 failures) before any test logic ran.
 *
 * Unit tests construct their own dependencies (mockk / in-memory Room), so
 * a plain Application is all they need. Hilt-dependent behaviour is covered
 * by the instrumentation tests (HiltTestRunner, on-device keystore real).
 */
class TestClinicApplication : Application()
