package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity
import com.aistudio.clinicsystem.data.db.UserEntity

/**
 * P-03 refactor: Home tab content for PatientScreen Bottom Navigation.
 *
 * Shows: greeting banner + quick stats + queue snapshot.
 * Compact overview — for full appointments list see PatientAppointmentsTab.
 */
@Composable
fun PatientHomeTab(
    currentUser: UserEntity?,
    appointments: List<AppointmentEntity>,
    records: List<MedicalRecordEntity>,
    cachedQueueSnapshots: List<QueueSnapshotEntity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderGreetingBanner(
            userName = currentUser?.fullName ?: "Пациент",
            activeAppointmentsCount = appointments.count { it.status == "PENDING" || it.status == "APPROVED" },
            completedRecordsCount = records.size
        )

        CachedQueueSnapshotsCard(
            snapshots = cachedQueueSnapshots
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
