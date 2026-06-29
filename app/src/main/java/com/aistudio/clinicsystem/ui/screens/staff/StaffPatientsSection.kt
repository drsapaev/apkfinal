package com.aistudio.clinicsystem.ui.screens.staff

import com.aistudio.clinicsystem.ui.theme.Spacing
import com.aistudio.clinicsystem.ui.theme.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.UserEntity

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
    onWriteRecord: (String) -> Unit
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.FolderShared,
                contentDescription = null,
                tint = adminColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                text = "Справочник пациентов и медкарты",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    val displayPatients = patientRoleUsers.filter {
        if (searchQuery.isNotBlank()) {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery)
        } else true
    }

    if (displayPatients.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.large))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.large))
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Пациенты не найдены.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
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
                accentColor = adminColor
            )
        }
    }
}
