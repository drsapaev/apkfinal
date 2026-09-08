// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  // BUILD-FIX: Kotlin Android plugin must be on the classpath for the app
  // module (the Compose compiler plugin alone does not compile Kotlin).
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.detekt) apply false
  // Stage 2.1: Hilt — applied here (false) so the plugin is on the classpath
  // for the app module to apply. Hilt 2.51.1 supports KSP (no kapt needed).
  alias(libs.plugins.hilt) apply false
}
