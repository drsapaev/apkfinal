plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.secrets)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.aistudio.clinicsystem"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "$rootDir/my-upload-key.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        create("debugConfig") {
            storeFile = file("$rootDir/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            // E2.7: production backend URL. Override via gradle property for staging.
            buildConfigField("String", "BASE_URL", "\"https://api.clinic.example.com/\"")
            buildConfigField("String", "BACKEND_URL", "\"https://api.clinic.example.com/\"")
            // E3.6: backend exposes /ws/queue for real-time queue updates (not bare /ws)
            buildConfigField("String", "WEBSOCKET_URL", "\"wss://api.clinic.example.com/ws/queue\"")
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
            // E2.7: debug backend URL — Android emulator maps 10.0.2.2 to host's 127.0.0.1.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:18000/\"")
            buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:18000/\"")
            // E3.6: backend exposes /ws/queue for real-time queue updates (not bare /ws)
            buildConfigField("String", "WEBSOCKET_URL", "\"ws://10.0.2.2:18000/ws/queue\"")
            // E2.6: debug source set provides permissive network_security_config.xml
            // (cleartext permitted for 10.0.2.2/localhost). Release uses the strict
            // version in src/main/res/xml/.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    // Redirect temp files to a roomier filesystem (root /tmp is small)
    systemProperty("java.io.tmpdir", "/home/z/.robolectric-tmp")
    // Enable HTTP for test resources (Robolectric downloads Android jars on first run)
    systemProperty("robolectric.offline", "false")
    systemProperty("robolectric.dataDir", "/home/z/.robolectric-dataDir")
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
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
    implementation(libs.androidx.biometric)
    // implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    // implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    // implementation(libs.firebase.ai)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    // implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")
    implementation("net.zetetic:sqlcipher-android:4.5.4")
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

// M3A/E7.2: detekt configuration
detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt.yml")
    baseline = file("$rootDir/config/detekt-baseline.xml")
    parallel = false
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
