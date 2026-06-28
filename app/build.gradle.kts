plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.secrets)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    // Stage 0.3: Jacoco is a Gradle CORE plugin — applied directly, no alias.
    jacoco
    // Stage 2.1: Hilt DI — replaces manual singleton wiring with @Inject / @Singleton.
    // Uses KSP processor (no kapt — faster, compatible with Kotlin 2.1.x).
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.aistudio.clinicsystem"
    // Stage 1.4 (fix M5 build): the block-with-release() DSL is not a real
    // AGP API. Use the standard integer form. If SDK extension 1 is
    // required for a specific API, add `compileSdkExtension = 1` after
    // verifying the dependency actually needs it.
    compileSdk = 36

    // M1/E4.1: Room schema export directory. KSP will write a JSON schema
    // per database version to app/schemas/. These files MUST be committed
    // to git — they are the baseline for MigrationTestHelper (E4.4).
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    defaultConfig {
        applicationId = "com.aistudio.clinicsystem"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "com.aistudio.clinicsystem.HiltTestRunner"

        // Stage 4.5 (C-8 fix): Privacy Policy URL placeholder.
        // Substituted into AndroidManifest via ${privacyPolicyUrl}.
        // Default is a placeholder; release builds MUST override via
        // `clinic.privacyPolicyUrl` gradle property (CI secret).
        val privacyUrl =
            (project.findProperty("clinic.privacyUrl") as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "https://clinic.tld/privacy"
        manifestPlaceholders["privacyPolicyUrl"] = privacyUrl
    }

    signingConfigs {
        // Stage 1.5 (Critical fix C1/C2 build): the previous release config
        // pointed at `$rootDir/my-upload-key.jks` which does not exist in the
        // repo. The build would fail at the `packageRelease` step. Now the
        // release signing config is created ONLY when the keystore env vars
        // are present — debug-only builds (where the config is unused) no
        // longer fail.
        //
        // CI decodes a CI-only keystore from `secrets.CI_RELEASE_KEYSTORE_BASE64`
        // and sets KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD env vars
        // (see `.github/workflows/android.yml`).
        //
        // Local developers: set the env vars in your shell, OR drop a
        // real keystore at `$rootDir/my-upload-key.jks` (gitignored).
        if (System.getenv("STORE_PASSWORD") != null) {
            create("release") {
                val keystorePath =
                    System.getenv("KEYSTORE_PATH")
                        ?: "$rootDir/my-upload-key.jks"
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
        // Stage 1.5 (Critical fix C2 build): the previous `debugConfig`
        // pointed at `$rootDir/debug.keystore` which does not exist in
        // the repo. AGP auto-creates a debug keystore at
        // `~/.android/debug.keystore` when no signingConfig is set on the
        // debug build type — so we delete the custom config entirely and
        // rely on the default. This means `./gradlew assembleDebug` works
        // on a fresh clone without any setup.
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Stage 1.5: only attach the release signing config if it was
            // actually created (i.e. env vars are set). When absent, AGP
            // leaves the release APK unsigned — `assembleRelease` still
            // succeeds (R8/ProGuard/resources shrinking all run), but the
            // APK cannot be installed. This is the desired behavior for
            // CI smoke tests that don't have the real keystore.
            if (System.getenv("STORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }

            // Stage 1.3 (Critical fix C-3 / NET-4): production backend URL.
            // The previous value was a placeholder `api.clinic.example.com`
            // (IANA-reserved) — the shipped app could not reach any backend.
            //
            // The real URL MUST be provided via a Gradle property at build
            // time, e.g.:
            //   ./gradlew assembleRelease -Pclinic.baseUrl=https://api.clinic.tld/ -Pclinic.wsUrl=wss://api.clinic.tld/ws/queue
            //
            // Or via `~/.gradle/gradle.properties`:
            //   clinic.baseUrl=https://api.clinic.tld/
            //   clinic.wsUrl=wss://api.clinic.tld/ws/queue
            //
            // CI injects these from GitHub Actions secrets (see
            // `.github/workflows/android.yml` job `release-smoke`).
            //
            // If the property is missing, the build produces an obviously
            // invalid URL (`https://INVALID.unset-base-url.example/`) so
            // any release artifact built without the property fails at
            // runtime with a clear error, not silently at a parked domain.
            val prodBaseUrl =
                (project.findProperty("clinic.baseUrl") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.contains("example.com") }
                    ?: "https://INVALID.unset-base-url.example/"
            val prodWsUrl =
                (project.findProperty("clinic.wsUrl") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.contains("example.com") }
                    ?: "wss://INVALID.unset-base-url.example/ws/queue"

            buildConfigField("String", "BASE_URL", "\"$prodBaseUrl\"")
            buildConfigField("String", "BACKEND_URL", "\"$prodBaseUrl\"")
            buildConfigField("String", "WEBSOCKET_URL", "\"$prodWsUrl\"")

            // Stage 4.3 (H-3 build fix): Certificate Pinning.
            // Pins are SHA-256 hashes of the server's public key.
            // Read from gradle properties `clinic.certPin1` (current key)
            // and `clinic.certPin2` (backup for next rotation).
            // If unset, NO pins are configured (development mode — debug
            // builds against 10.0.2.2 don't need pinning). Release builds
            // MUST set the pins via CI secrets.
            val certPin1 =
                (project.findProperty("clinic.certPin1") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "UNSET"
            val certPin2 =
                (project.findProperty("clinic.certPin2") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: "UNSET"
            buildConfigField("String", "CERT_PIN_PRIMARY", "\"$certPin1\"")
            buildConfigField("String", "CERT_PIN_BACKUP", "\"$certPin2\"")
        }
        debug {
            // Stage 1.5: no custom signingConfig — AGP auto-creates a debug
            // keystore at `~/.android/debug.keystore` on first build.
            // E2.7: debug backend URL — Android emulator maps 10.0.2.2 to host's 127.0.0.1.
            // Can be overridden with `-Pclinic.debugBaseUrl=...` for staging.
            val debugBaseUrl =
                (project.findProperty("clinic.debugBaseUrl") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "http://10.0.2.2:18000/"
            val debugWsUrl =
                (project.findProperty("clinic.debugWsUrl") as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "ws://10.0.2.2:18000/ws/queue"
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
            buildConfigField("String", "BACKEND_URL", "\"$debugBaseUrl\"")
            buildConfigField("String", "WEBSOCKET_URL", "\"$debugWsUrl\"")
            // E2.6: debug source set provides permissive network_security_config.xml
            // (cleartext permitted for 10.0.2.2/localhost). Release uses the strict
            // version in src/main/res/xml/.
            // Stage 0.3: enable Jacoco coverage instrumentation in debug builds.
            // The release variant does NOT get coverage (smaller APK, no perf hit).
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
        }
    }
    compileOptions {
        // Stage 1.4: Kotlin 2.1.x targets JVM 17 by default. Java 11
        // source/target compatibility produced `IncompatibleJvmTargetError`
        // warnings and prevented some Kotlin 2.x optimizations.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// M1/E4.4: configure test JVM with conservative memory limits to avoid
// container OOM kills during Robolectric tests. The forked test JVM needs
// enough heap for Robolectric's Android framework simulation but must stay
// within the container's 8GB cgroup limit.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Robolectric + mockk need heap for the simulated Android framework
    maxHeapSize = "1g"
    jvmArgs("-XX:MaxMetaspaceSize=384m")
    // Redirect temp files to /tmp/robolectric-tmp (created in CI workflow
    // and locally before running tests)
    systemProperty("java.io.tmpdir", "/tmp/robolectric-tmp")
    // Enable HTTP for test resources (Robolectric downloads Android jars on first run)
    systemProperty("robolectric.offline", "false")
    // Enable headless mode for AWT (Robolectric may trigger it)
    systemProperty("java.awt.headless", "true")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // Stage 2.4: Firebase BOM + firestore + firebase-ai REMOVED.
    // FirestoreSyncManager was dead code (no-op shim), and the Firebase
    // plugin was never applied (no google-services.json) — any code path
    // that called FirebaseFirestore.getInstance() would crash. The
    // real-time channel is OkHttp WebSocket via RealtimeManager.
    // implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    // implementation(libs.androidx.camera.camera2)
    // implementation(libs.androidx.camera.core)
    // implementation(libs.androidx.camera.lifecycle)
    // implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // Stage 7.3 (UI-18 fix): Splash Screen API
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.biometric)
    // implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Stage 2.9: ProcessLifecycleOwner — used to tie WebSocket lifecycle
    // to app foreground/background transitions (ON_START → connect,
    // ON_STOP → disconnect).
    implementation(libs.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    // implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    // implementation(libs.play.services.location)
    implementation(libs.retrofit)
    // Stage 4.9 (M-4 build fix): upgraded to stable versions with 16KB
    // page-size alignment support (required for Android 15+ devices with
    // 16KB kernels — Play Store requirement for new apps targeting SDK 36).
    //   - security-crypto 1.1.0 stable (was 1.1.0-alpha06)
    //   - sqlcipher-android 4.6.0 (was 4.5.4 — added 16KB alignment)
    implementation("androidx.security:security-crypto-ktx:1.1.0")
    implementation("net.zetetic:sqlcipher-android:4.6.0")

    // Stage 2.1: Hilt DI
    implementation(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
    // Stage 2.7: Hilt Navigation Compose — hiltViewModel() support
    implementation(libs.hilt.navigation.compose)
    // Stage 2.5: Hilt Work — inject SyncWorker dependencies
    implementation(libs.hilt.work)
    "ksp"(libs.hilt.compiler.androidx)

    // Stage 4.1: Timber — logger with BuildConfig.DEBUG gating.
    // Release builds plant a ReleaseTree that drops DEBUG/INFO logs and
    // strips PHI from WARN/ERROR logs (C-4 final fix).
    implementation(libs.timber.lib)

    // Stage 4.6: Play Integrity API — attest device integrity at login
    // and on sensitive operations (create medical record).
    implementation(libs.play.integrity)

    // Stage 7.2 (PERF-4 fix): Baseline Profile installer — speeds up
    // cold start by ~30% by pre-compiling hot paths. The actual
    // baseline-prof.txt file is generated by a Macrobenchmark module
    // (to be added in Stage 10). For now, profileinstaller is wired
    // so that when the profile is added, it's automatically used.
    implementation(libs.profileinstaller)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    // Stage 4 fix: Hilt testing — HiltTestApplication + @HiltAndroidTest support.
    // HiltTestApplication itself does NOT need KSP codegen (it's a library class);
    // only @HiltAndroidTest-annotated test classes that use @TestInstallIn need it.
    // We'll add kspAndroidTest when we write the first @HiltAndroidTest in Stage 10.
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register<Copy>("exportApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(project.rootDir.resolve("release_apk"))
}

// ─────────────────────────────────────────────────────────────────────────
// Stage 0.3: Jacoco coverage report
//
// Aggregates unit-test + instrumentation coverage into a single XML/HTML
// report. CI runs `./gradlew jacocoTestReport` after tests, then calls
// `.github/scripts/coverage-gate.py` to enforce minimum coverage on
// `data/` and `domain/` packages.
//
// Coverage gate thresholds:
//   - Overall: ≥ 50% (lifted as codebase matures)
//   - `data/` package: ≥ 70%
//   - `domain/` package: ≥ 70%
// ─────────────────────────────────────────────────────────────────────────
tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates Jacoco coverage report for debug build (unit + instrumentation)."

    // Coverage data sources — AGP 8.x uses different paths than 7.x.
    // Include all common patterns so the report works regardless of AGP version.
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "outputs/unit_test_execution_data/Debug/unitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/*.ec",
                "jacoco/testDebugUnitTest.exec",
                "outputs/code-coverage/connected/*.ec",
            )
        },
    )

    // What to report — only first-party Kotlin sources
    val mainSources = file("$projectDir/src/main/java")
    val mainClasses =
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
            include("**/com/aistudio/clinicsystem/**")
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.class", "**/Manifest*.class")
        }
    sourceDirectories.setFrom(mainSources)
    classDirectories.setFrom(mainClasses)

    // Output both XML (CI gate) and HTML (human review)
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
        csv.required.set(false)
    }

    // Run after unit tests (instrumentation is separate — connectedCheck)
    dependsOn("testDebugUnitTest")
}

// Stage 0.2: detekt configuration — baseline DELETED, all rules enforced.
// Previous ~100 suppressed smells are now either excluded via config
// (Compose FunctionNaming false positives) or tracked as cleanup tickets.
detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt.yml")
    // No baseline — every issue fails CI. New code must be clean; existing
    // smells surface on the files they live in (detekt evaluates the whole
    // module, but PRs only need to keep NEW code clean — see
    // `detekt` task type for incremental mode in future).
    parallel = true
    ignoreFailures = false
    autoCorrect = false
}

// M3A/E7.2: ktlint configuration
ktlint {
    version.set("1.3.1")
    debug.set(false)
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}
