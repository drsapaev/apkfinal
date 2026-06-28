package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Stage 10d (PERF-8 fix): StaffCreateAppointmentDialog — extracted from
 * StaffScreen.kt (reduces it by ~150 LOC).
 *
 * The create appointment dialog with:
 *  - Patient phone + name
 *  - Doctor selection (dropdown)
 *  - Specialty selection
 *  - Date + time
 *  - Reason
 *  - Create / Cancel actions
 *
 * State is hoisted via parameters.
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
) {
    if (!visible) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
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

                OutlinedTextField(
                    value = patientPhone,
                    onValueChange = onPatientPhoneChange,
                    label = { Text("Телефон пациента") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = patientName,
                    onValueChange = onPatientNameChange,
                    label = { Text("ФИО пациента") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = doctorSelected,
                    onValueChange = onDoctorSelectedChange,
                    label = { Text("Врач") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                    OutlinedTextField(
                        value = date,
                        onValueChange = onDateChange,
                        label = { Text("Дата") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = onTimeChange,
                        label = { Text("Время") },
                        singleLine = true,
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
}
