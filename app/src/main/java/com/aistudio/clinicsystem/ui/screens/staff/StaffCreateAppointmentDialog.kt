package com.aistudio.clinicsystem.ui.screens.staff

import com.aistudio.clinicsystem.ui.theme.Radius
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stage 10d (PERF-8 fix): StaffCreateAppointmentDialog — extracted from
 * StaffScreen.kt (reduces it by ~150 LOC).
 *
 * P-10 fix: replaced plain-text Date/Time fields with Material 3 DatePicker,
 * Doctor field with ExposedDropdownMenuBox, and added KeyboardType.Phone for
 * patient phone.
 *
 * The create appointment dialog with:
 *  - Patient phone (numeric keyboard) + name
 *  - Doctor selection (dropdown from doctorsList)
 *  - Specialty selection (auto-filled from doctor, editable)
 *  - Date (Material 3 DatePicker) + time (plain text with HH:mm hint)
 *  - Reason
 *  - Create / Cancel actions
 *
 * State is hoisted via parameters.
 *
 * @param doctorsList list of (doctorName, specialty) pairs for the dropdown.
 *        If empty, falls back to plain text input.
 */
@Composable
fun StaffCreateAppointmentDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    patientPhone: String,
    onPatientPhoneChange: (String) -> Unit,
    patientName: String,
    onPatientNameChange: (String) -> Unit,
    doctorSelected: String,
    onDoctorSelectedChange: (String) -> Unit,
    specialtySelected: String,
    onSpecialtySelectedChange: (String) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    time: String,
    onTimeChange: (String) -> Unit,
    reason: String,
    onReasonChange: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    doctorsList: List<Pair<String, String>> = emptyList(),
) {
    if (!visible) return

    var showDatePicker by remember { mutableStateOf(false) }
    var expandedDoctor by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(Radius.large),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Создать запись на приём",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // P-10 fix: numeric keyboard for phone
                OutlinedTextField(
                    value = patientPhone,
                    onValueChange = onPatientPhoneChange,
                    label = { Text("Телефон пациента") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = patientName,
                    onValueChange = onPatientNameChange,
                    label = { Text("ФИО пациента") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // P-10 fix: doctor dropdown if doctorsList is provided, else plain text
                if (doctorsList.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDoctor,
                        onExpandedChange = { expandedDoctor = it }
                    ) {
                        OutlinedTextField(
                            value = doctorSelected,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Врач") },
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDoctor)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDoctor,
                            onDismissRequest = { expandedDoctor = false }
                        ) {
                            doctorsList.forEach { (docName, spec) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = docName, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = spec,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onDoctorSelectedChange(docName)
                                        onSpecialtySelectedChange(spec)
                                        expandedDoctor = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = doctorSelected,
                        onValueChange = onDoctorSelectedChange,
                        label = { Text("Врач") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = specialtySelected,
                    onValueChange = onSpecialtySelectedChange,
                    label = { Text("Специальность") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // P-10 fix: Material 3 DatePicker for date field
                    OutlinedTextField(
                        value = date.ifBlank {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(Date())
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Дата") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Выбрать дату"
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = onTimeChange,
                        label = { Text("Время (ЧЧ:ММ)") },
                        singleLine = true,
                        placeholder = { Text("09:00") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Причина визита") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onCreate,
                        enabled = patientPhone.isNotBlank() && patientName.isNotBlank(),
                    ) {
                        Text("Создать запись")
                    }
                }
            }
        }
    }

    // P-10 fix: Material 3 DatePicker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(Date(millis))
                            onDateChange(formatted)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
