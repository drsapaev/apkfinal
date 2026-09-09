package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P-30: Compose UI tests for BookAppointmentDialog.
 *
 * Tests cover the 4-step booking flow:
 * - Step 1: Doctor selection (radio buttons)
 * - Step 2: Date selection (5 next days)
 * - Step 3: Time slot selection (7 slots)
 * - Step 4: Complaint input
 * - Confirm/Cancel buttons
 *
 * This dialog is a pure Composable with hoisted state — no ViewModel
 * needed, so tests can run without Hilt setup.
 *
 * Run: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BookAppointmentDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Locale-independent expectations: the device may run any locale
     *  (CI emulators default to en-US), so expected labels are resolved
     *  from resources instead of hardcoded Russian literals. */
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val testDoctors =
        listOf(
            Pair("Dr. Test Cardiologist", "Кардиолог"),
            Pair("Dr. Test Neurologist", "Невролог"),
        )

    private val testDates = listOf("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04", "2026-07-05")

    private val testTimeSlots = listOf("09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00")

    private fun setupDialog() {
        composeTestRule.setContent {
            MyApplicationTheme {
                BookAppointmentDialog(
                    doctors = testDoctors,
                    bookingDatesList = testDates,
                    timeSlots = testTimeSlots,
                    selectedDoctor = "",
                    selectedSpecialty = "",
                    selectedDateIdx = 0,
                    selectedTimeSlot = "",
                    bookingReasonInput = "",
                    isBookingInProgress = false,
                    onSelectDoctor = { _, _ -> },
                    onSelectDateIdx = { _ -> },
                    onSelectTimeSlot = { _ -> },
                    onReasonInputChange = { _ -> },
                    onConfirm = {},
                    onDismiss = {},
                    tealPrimary =
                        androidx.compose.ui.graphics
                            .Color(0xFF4DB6AC),
                    tealLight =
                        androidx.compose.ui.graphics
                            .Color(0xFFB2DFDB),
                    accentNavy =
                        androidx.compose.ui.graphics
                            .Color(0xFF1F2A37),
                )
            }
        }
    }

    @Test
    fun bookDialog_showsStepLabels() {
        setupDialog()

        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_select_specialist)).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_select_date)).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_select_time)).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.ui_describe_complaint)).assertIsDisplayed()
    }

    @Test
    fun bookDialog_showsDoctorNames() {
        setupDialog()

        composeTestRule.onNodeWithText("Dr. Test Cardiologist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dr. Test Neurologist").assertIsDisplayed()
    }

    @Test
    fun bookDialog_showsTimeSlots() {
        setupDialog()

        composeTestRule.onNodeWithText("09:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("16:00").assertIsDisplayed()
    }

    @Test
    fun bookDialog_cancelButton_isDisplayed() {
        setupDialog()

        composeTestRule.onNodeWithText("Отмена").assertIsDisplayed()
    }

    @Test
    fun bookDialog_showsBookingButton() {
        setupDialog()

        composeTestRule.onNodeWithText("Записаться").assertIsDisplayed()
    }
}
