package com.aistudio.clinicsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.aistudio.clinicsystem.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P2: Roborazzi screenshot tests for key UI components.
 *
 * These tests capture baseline screenshots and compare them on subsequent
 * runs. If a Compose layout changes, the test fails — catching unintended
 * visual regressions after refactoring.
 *
 * Run: ./gradlew verifyRoborazziDebug
 * Record new baselines: ./gradlew recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [33])
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `greeting screenshot`() {
        composeTestRule.setContent {
            MyApplicationTheme { Greeting("Robolectric") }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/greeting.png"
        )
    }

    @Test
    fun `error text screenshot`() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = "Неверный логин или пароль",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/error_text.png"
        )
    }

    @Test
    fun `theme light screenshot`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(
                        text = "Clinic System",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/theme_light.png"
        )
    }

    @Test
    fun `theme dark screenshot`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Text(
                        text = "Clinic System",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/theme_dark.png"
        )
    }
}
