package com.aistudio.clinicsystem.ui.screens.patient

import com.aistudio.clinicsystem.ui.theme.Spacing
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aistudio.clinicsystem.R
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

/**
 * P-07 refactor: extracted from PatientScreen.kt (was 758 LOC).
 *
 * 4-step booking dialog:
 *   1. Select doctor (radio list)
 *   2. Select date (next 5 days)
 *   3. Select time slot
 *   4. Describe complaint (multiline text)
 *
 * State is hoisted via parameters — caller owns all mutable state and
 * invokes viewModel.createAppointment() via [onConfirm].
 */
@Suppress("UnusedParameter")  // selectedSpecialty kept for API symmetry with caller
@Composable
fun BookAppointmentDialog(
    doctors: List<Pair<String, String>>,
    bookingDatesList: List<String>,
    timeSlots: List<String>,
    selectedDoctor: String,
    selectedSpecialty: String,
    selectedDateIdx: Int,
    selectedTimeSlot: String,
    bookingReasonInput: String,
    isBookingInProgress: Boolean,
    onSelectDoctor: (String, String) -> Unit,
    onSelectDateIdx: (Int) -> Unit,
    onSelectTimeSlot: (String) -> Unit,
    onReasonInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    tealPrimary: Color,
    tealLight: Color,
    accentNavy: Color
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(Radius.xl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.l)
                    .imePadding().verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.pat_new_appointment),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = accentNavy
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialogue")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.m))

                Text(
                    text = "1. ВЫБЕРИТЕ СПЕЦИАЛИСТА",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tealPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                doctors.forEach { (doc, spec) ->
                    val isSelected = selectedDoctor == doc
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(Radius.medium))
                            .background(if (isSelected) tealLight.copy(alpha = 0.6f) else Color.Transparent)
                            .border(
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) tealPrimary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(Radius.medium)
                            )
                            .clickable { onSelectDoctor(doc, spec.substringBefore(" (")) }
                            .padding(Spacing.s)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectDoctor(doc, spec.substringBefore(" (")) },
                                colors = RadioButtonDefaults.colors(selectedColor = tealPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = doc,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppFontSize.title,
                                    color = accentNavy
                                )
                                Text(
                                    text = spec,
                                    fontSize = AppFontSize.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))

                Text(
                    text = "2. ВЫБЕРИТЕ ДАТУ ПРИЁМА",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tealPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    bookingDatesList.forEachIndexed { idx, dStr ->
                        val isSelected = selectedDateIdx == idx
                        val parts = dStr.split("-")
                        val day = parts.getOrNull(2) ?: dStr
                        val month = parts.getOrNull(1) ?: ""

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.95f)
                                .clip(RoundedCornerShape(Radius.medium))
                                .background(if (isSelected) tealPrimary else MaterialTheme.colorScheme.surface)
                                .border(
                                    border = BorderStroke(1.dp, if (isSelected) tealPrimary else MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(Radius.medium)
                                )
                                .clickable { onSelectDateIdx(idx) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppFontSize.titleLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else accentNavy
                                )
                                Text(
                                    // P-28 fix: Locale-aware month name
                                    text = remember(month) {
                                        try {
                                            val monthNum = month.toIntOrNull()
                                            if (monthNum != null && monthNum in 1..12) {
                                                val cal = Calendar.getInstance()
                                                cal.set(Calendar.MONTH, monthNum - 1)
                                                SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
                                            } else month
                                        } catch (e: Exception) { month }
                                    },
                                    fontSize = AppFontSize.caption,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))

                Text(
                    text = "3. ВЫБЕРИТЕ ВРЕМЯ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tealPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val row1 = timeSlots.take(4)
                    val row2 = timeSlots.drop(4)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row1.forEach { slot ->
                            TimeSlotButton(
                                slot = slot,
                                isSelected = selectedTimeSlot == slot,
                                onSelect = onSelectTimeSlot
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(end = 40.dp)
                    ) {
                        row2.forEach { slot ->
                            TimeSlotButton(
                                slot = slot,
                                isSelected = selectedTimeSlot == slot,
                                onSelect = onSelectTimeSlot
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))

                Text(
                    text = "4. ОПИШИТЕ ЖАЛОБЫ / ПРИЧИНУ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tealPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = bookingReasonInput,
                    onValueChange = onReasonInputChange,
                    placeholder = { Text("Например: плановый осмотр, острая боль...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_reason_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tealPrimary,
                        focusedLabelColor = tealPrimary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.l))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.ui_otmena), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Button(
                        onClick = onConfirm,
                        enabled = !isBookingInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                        shape = RoundedCornerShape(Radius.medium),
                        modifier = Modifier
                            .testTag("confirm_booking_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        if (isBookingInProgress) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.ui_zapisatsya), color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotButton(
    slot: String,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    tealPrimary: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radius.small))
            .background(if (isSelected) tealPrimary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onSelect(slot) }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = slot,
            fontSize = AppFontSize.body,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
