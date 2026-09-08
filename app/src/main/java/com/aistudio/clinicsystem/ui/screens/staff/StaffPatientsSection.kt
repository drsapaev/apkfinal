package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing

/**
 * Section 2: Patients Directory header + list.
 *
 * P-02 refactor: extracted from StaffScreen.kt. Returns LazyListScope items
 * for the patients header and list. Caller is responsible for state hoisting
 * (searchQuery, selectedPatientPhone, showAddRecordDialog).
 */
fun LazyListScope.staffPatientsSection(
    patientRoleUsers: List<UserEntity>,
    allRecords: List<MedicalRecordEntity>,
    searchQuery: String,
    adminColor: Color,
    onWriteRecord: (String) -> Unit,
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.FolderShared,
                contentDescription = null,
                tint = adminColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                text = stringResource(R.string.staff_patients_dir),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    val displayPatients =
        patientRoleUsers.filter {
            if (searchQuery.isNotBlank()) {
                it.fullName.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery)
            } else {
                true
            }
        }

    if (displayPatients.isEmpty()) {
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.large))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.large))
                        .padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.staff_no_patients),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        items(displayPatients, key = { it.id }) { patient ->
            val patientRecords = allRecords.filter { it.patientPhone == patient.phone }
            StaffPatientCardItem(
                patient = patient,
                recordsCount = patientRecords.size,
                onWriteRecord = { onWriteRecord(patient.phone) },
                accentColor = adminColor,
            )
        }
    }
}

/**
 * TASK-9: clinical patient REGISTRY section (GET /api/v1/patients?q=…) with
 * search + pagination. This is the clinical directory — deliberately separate
 * from the local auth-user table and from the Admin system-users list.
 * Rendered as LazyListScope items to participate in the staff LazyColumn.
 */
fun LazyListScope.staffPatientRegistrySection(
    patients: List<com.aistudio.clinicsystem.data.api.StaffPatientDto>,
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    adminColor: Color,
    onWriteRecord: (String) -> Unit,
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.FolderShared,
                contentDescription = null,
                tint = adminColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                text = "Справочник пациентов",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.s))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("patient_registry_search"),
            placeholder = { Text("Поиск по имени или телефону") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        when {
            error != null -> {
                Text(
                    text = "Ошибка загрузки справочника: $error",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppFontSize.body,
                )
            }
            loading && patients.isEmpty() -> {
                Text(
                    text = "Загрузка справочника…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = AppFontSize.body,
                )
            }
            patients.isEmpty() -> {
                Text(
                    text = "Пациенты не найдены. Измените запрос или обновите поиск.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = AppFontSize.body,
                )
            }
        }
    }
    items(patients, key = { "registry-${it.id}" }) { patient ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppFontSize.body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = patient.phone ?: "телефон не указан",
                    fontSize = AppFontSize.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = { onWriteRecord(patient.phone ?: "") },
            ) {
                Text("Медкарта", color = adminColor, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (patients.isNotEmpty() && !loading) {
        item {
            androidx.compose.material3.TextButton(onClick = onLoadMore) {
                Text("Показать ещё", color = adminColor)
            }
        }
    }
}
