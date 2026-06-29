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
import com.aistudio.clinicsystem.data.db.PendingSyncEntity

/**
 * P-03 refactor: Appointments tab content for PatientScreen Bottom Navigation.
 *
 * Shows: segment tabs (All/Active/Finished) + appointments list with cancel.
 */
@Composable
fun PatientAppointmentsTab(
    appointments: List<AppointmentEntity>,
    pendingSyncs: List<PendingSyncEntity>,
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    onCancelClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredAppointments = when (selectedFilter) {
        "ACTIVE" -> appointments.filter { it.status == "PENDING" || it.status == "APPROVED" }
        "FINISHED" -> appointments.filter { it.status == "COMPLETED" || it.status == "CANCELLED" }
        else -> appointments
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppointmentSegmentTabs(
            selectedFilter = selectedFilter,
            onFilterSelect = onFilterSelect
        )

        AppointmentsSessionList(
            appointments = filteredAppointments,
            pendingSyncs = pendingSyncs,
            onCancelClick = onCancelClick
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
