package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.ui.res.stringResource
import com.aistudio.clinicsystem.ui.theme.Spacing
import com.aistudio.clinicsystem.ui.theme.Radius
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Stage 10d (PERF-8 fix): StaffMedicalRecordDialog — extracted from
 * StaffScreen.kt (was 1748 LOC; this extraction reduces it by ~200 LOC).
 *
 * The medical record creation dialog with:
 *  - Patient selection
 *  - Clinical diagnosis input
 *  - Prescription input
 *  - Recommendations input
 *  - Quick diagnosis badges (template shortcuts)
 *  - Save / Cancel actions
 *
 * State is hoisted via parameters — the parent (StaffScreen) owns
 * the state and passes it down.
 */
@Composable
fun StaffMedicalRecordDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    selectedPatientPhone: String,
    onPatientPhoneChange: (String) -> Unit,
    diagnosis: String,
    onDiagnosisChange: (String) -> Unit,
    prescription: String,
    onPrescriptionChange: (String) -> Unit,
    recommendations: String,
    onRecommendationsChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

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
                    .padding(Spacing.xl)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text = "Внести запись в медицинскую карту",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Patient phone
                OutlinedTextField(
                    value = selectedPatientPhone,
                    onValueChange = onPatientPhoneChange,
                    label = { Text(stringResource(R.string.ui_patient_phone)) },
                    placeholder = { Text("+7...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Clinical diagnosis
                OutlinedTextField(
                    value = diagnosis,
                    onValueChange = onDiagnosisChange,
                    label = { Text("Клинический диагноз") },
                    placeholder = { Text("Острый пульпит, гипертония 2 ст. и др.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Prescription
                OutlinedTextField(
                    value = prescription,
                    onValueChange = onPrescriptionChange,
                    label = { Text("Назначения и Рецептурный лист") },
                    placeholder = { Text("Принимать Лозартан 50мг по утрам") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                // Recommendations
                OutlinedTextField(
                    value = recommendations,
                    onValueChange = onRecommendationsChange,
                    label = { Text(stringResource(R.string.ui_recommendations)) },
                    placeholder = { Text("Повторный визит через две недели.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Spacer(modifier = Modifier.height(Spacing.s))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ui_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Button(
                        onClick = onSave,
                        enabled = selectedPatientPhone.isNotEmpty() && diagnosis.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.ui_save_to_db), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
