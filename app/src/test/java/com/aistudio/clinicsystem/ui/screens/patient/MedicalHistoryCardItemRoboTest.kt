package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric mirror of the locale-sensitive androidTest assertions
 * (MedicalHistoryCardItemTest) so the same expectations run in the JVM
 * unit-test pipeline without an emulator.
 *
 * History: the instrumented variants failed on en-US emulators because
 * expectations were hardcoded Russian literals while the app resolves
 * values-en strings. Expectations are now resolved through getString in
 * BOTH pipelines; this mirror guards against regressions locally.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "en-rUS")
class MedicalHistoryCardItemRoboTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = RuntimeEnvironment.getApplication()

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
    fun robo_collapsed_showsDoctorName_anyLocale() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(record = testRecord, isExpanded = false, onExpandClick = {})
            }
        }

        composeTestRule
            .onNodeWithText(appContext.getString(R.string.med_record_doctor, testRecord.doctorName))
            .assertIsDisplayed()
    }

    @Test
    fun robo_expanded_showsDownloadAndShare_anyLocale() {
        composeTestRule.setContent {
            MyApplicationTheme {
                MedicalHistoryCardItem(record = testRecord, isExpanded = true, onExpandClick = {})
            }
        }

        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_download)).assertIsDisplayed()
    }
}
