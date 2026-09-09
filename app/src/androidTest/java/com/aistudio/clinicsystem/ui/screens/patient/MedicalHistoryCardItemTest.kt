package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P-30: Compose UI tests for MedicalHistoryCardItem.
 *
 * Tests cover:
 * - Collapsed state: diagnosis always visible (P-25 fix)
 * - Doctor name and visit date visible
 * - Expanded state: prescription, recommendations visible
 * - P-25 fix: Download and Share buttons visible when expanded
 *
 * This card is a pure Composable with hoisted state — no ViewModel needed.
 *
 * Run: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MedicalHistoryCardItemTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Locale-independent expectations: the device may run any locale
     *  (CI emulators default to en-US), so expected labels are resolved
     *  from resources instead of hardcoded Russian literals. */
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val testRecord =
        MedicalRecordEntity(
            id = "test-1",
            serverId = 1,
            patientPhone = "+79991234567",
            doctorName = "Dr. Test",
            diagnosis = "Тестовый диагноз",
            prescription = "Тестовый рецепт",
            visitDate = "2026-06-29",
            recommendations = "Тестовые рекомендации",
        )

    @Test
    fun medicalCard_collapsed_showsDiagnosis() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = false,
                    onExpandClick = {},
                )
            }
        }

        // Diagnosis always visible (even when collapsed)
        composeTestRule.onNodeWithText("Тестовый диагноз").assertIsDisplayed()
    }

    @Test
    fun medicalCard_collapsed_showsDoctorName() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = false,
                    onExpandClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(appContext.getString(R.string.med_record_doctor, testRecord.doctorName)).assertIsDisplayed()
    }

    @Test
    fun medicalCard_collapsed_showsVisitDate() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = false,
                    onExpandClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("2026-06-29").assertIsDisplayed()
    }

    @Test
    fun medicalCard_expanded_showsDownloadButton() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = true,
                    onExpandClick = {},
                )
            }
        }

        // P-25 fix: Download button visible when expanded
        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_download)).assertIsDisplayed()
    }

    @Test
    fun medicalCard_expanded_showsShareButton() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = true,
                    onExpandClick = {},
                )
            }
        }

        // P-25 fix: Share button visible when expanded
        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_share)).assertIsDisplayed()
    }

    @Test
    fun medicalCard_expanded_showsPrescription() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = true,
                    onExpandClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Тестовый рецепт").assertIsDisplayed()
    }

    @Test
    fun medicalCard_expanded_showsRecommendations() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(
                    record = testRecord,
                    isExpanded = true,
                    onExpandClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Тестовые рекомендации").assertIsDisplayed()
    }
}
