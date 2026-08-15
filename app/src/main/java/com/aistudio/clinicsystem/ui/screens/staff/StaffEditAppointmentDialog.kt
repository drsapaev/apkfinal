package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing

/**
 * P-02 completion: extracted from StaffScreen.kt — edit appointment dialog.
 *
 * Allows staff to edit patient phone/name, doctor, specialty, date, time,
 * reason, and status of an existing appointment.
 *
 * State is hoisted via parameters.
 */
@Composable
fun StaffEditAppointmentDialog(
    visible: Boolean,
    editPatientPhone: String,
    onPatientPhoneChange: (String) -> Unit,
    editPatientName: String,
    onPatientNameChange: (String) -> Unit,
    editDoctorSelected: String,
    onDoctorSelectedChange: (String) -> Unit,
    editSpecialtySelected: String,
    onSpecialtySelectedChange: (String) -> Unit,
    editDate: String,
    onDateChange: (String) -> Unit,
    editTime: String,
    onTimeChange: (String) -> Unit,
    editReason: String,
    onReasonChange: (String) -> Unit,
    editStatusSelected: String,
    onStatusSelectedChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    if (!visible) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(Radius.xl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                Text(
                    text = stringResource(R.string.dlg_edit_appointment),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = editPatientPhone,
                    onValueChange = onPatientPhoneChange,
                    label = { Text(stringResource(R.string.dlg_patient_phone)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editPatientName,
                    onValueChange = onPatientNameChange,
                    label = { Text(stringResource(R.string.dlg_patient_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editDoctorSelected,
                    onValueChange = onDoctorSelectedChange,
                    label = { Text(stringResource(R.string.dlg_doctor)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editSpecialtySelected,
                    onValueChange = onSpecialtySelectedChange,
                    label = { Text(stringResource(R.string.dlg_specialty)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    OutlinedTextField(
                        value = editDate,
                        onValueChange = onDateChange,
                        label = { Text(stringResource(R.string.dlg_date)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = editTime,
                        onValueChange = onTimeChange,
                        label = { Text(stringResource(R.string.dlg_time)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = editReason,
                    onValueChange = onReasonChange,
                    label = { Text(stringResource(R.string.dlg_reason_visit)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editStatusSelected,
                    onValueChange = onStatusSelectedChange,
                    label = { Text(stringResource(R.string.dlg_status)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.s))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ui_otmena))
                    }
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text(stringResource(R.string.ui_sohranit), color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }
    }
}
