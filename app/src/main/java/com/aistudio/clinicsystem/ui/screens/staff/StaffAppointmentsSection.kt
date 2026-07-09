package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius

/**
 * High-6 audit fix: StaffAppointmentsSection — extracted from
 * StaffScreen.kt (~290 LOC block).
 *
 * Contains:
 *   - Section header with "Approvals" title + "Create Appointment" button
 *   - Search field (by patient name or phone)
 *   - Today-only filter toggle
 *   - Doctor filter chips (horizontal scroll)
 *   - Status filter chips (horizontal scroll)
 *   - Filtered appointments list (StaffAppointmentCardItem)
 *   - Empty state when no appointments match filters
 *
 * State is hoisted via parameters — the parent (StaffScreen) owns
 * the filter state and search query, this composable renders them.
 *
 * Doctor filter auto-selects the current user's name if they're a doctor
 * (jobTitle == "DOCTOR" or fullName starts with "Dr.").
 */
@Composable
fun StaffAppointmentsSection(
    allAppointments: List<AppointmentEntity>,
    allPendingSyncs: List<PendingSyncEntity>,
    patientRoleUsers: List<UserEntity>,
    currentUser: UserEntity?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterTodayOnly: Boolean,
    onFilterTodayOnlyChange: (Boolean) -> Unit,
    selectedDoctorFilter: String,
    onDoctorFilterChange: (String) -> Unit,
    selectedStatusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    todayDateStr: String,
    adminColor: Color,
    onCreateAppointmentClick: () -> Unit,
    onApprove: (AppointmentEntity) -> Unit,
    onCancelClick: (AppointmentEntity) -> Unit,
    onAddNotesClick: (AppointmentEntity) -> Unit,
    onEditClick: (AppointmentEntity) -> Unit,
    onRegisterQueue: (AppointmentEntity) -> Unit,
) {
    val allDoctorsInSystem = remember(allAppointments) {
        allAppointments.map { it.doctorName }.distinct().filter { it.isNotBlank() }
    }
    val isCurrentUserDoctor = currentUser?.fullName?.startsWith("Dr.") == true ||
        currentUser?.jobTitle == "DOCTOR"

    LaunchedEffect(isCurrentUserDoctor, currentUser) {
        if (isCurrentUserDoctor && currentUser != null) {
            onDoctorFilterChange(currentUser!!.fullName)
        }
    }

    val doctorsList = buildList {
        add(stringResource(R.string.dlg_all_doctors))
        if (isCurrentUserDoctor && currentUser != null) {
            if (!contains(currentUser!!.fullName)) add(currentUser!!.fullName)
        }
        addAll(allDoctorsInSystem.filter { it != currentUser?.fullName })
    }.distinct()

    val statusesList = listOf(
        stringResource(R.string.dlg_all_statuses),
        "PENDING",
        "APPROVED",
        "COMPLETED",
        "CANCELLED",
    )

    val displayAppointments = allAppointments.filter { appt ->
        val matchesToday = if (filterTodayOnly) appt.date == todayDateStr else true
        val matchesSearch = if (searchQuery.isNotBlank()) {
            appt.patientName.contains(searchQuery, ignoreCase = true) ||
                appt.patientPhone.contains(searchQuery)
        } else true
        val matchesDoctor = if (selectedDoctorFilter != stringResource(R.string.dlg_all_doctors)) {
            appt.doctorName == selectedDoctorFilter
        } else true
        val matchesStatus = if (selectedStatusFilter != stringResource(R.string.dlg_all_statuses)) {
            appt.status == selectedStatusFilter
        } else true
        matchesToday && matchesSearch && matchesDoctor && matchesStatus
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Section header + Create Appointment button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AssignmentLate,
                    contentDescription = null,
                    tint = adminColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.staff_approvals),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Button(
                onClick = onCreateAppointmentClick,
                colors = ButtonDefaults.buttonColors(containerColor = adminColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("create_appointment_btn"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.ui_priem),
                    fontSize = AppFontSize.bodySmall,
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Поиск по ФИО или номеру телефона...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.dlg_clear))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = adminColor,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("appointment_search_field"),
        )

        // Today-only filter toggle
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChipBox(
                text = "Все записи (${allAppointments.size})",
                isSelected = !filterTodayOnly,
                accentColor = adminColor,
                onClick = { onFilterTodayOnlyChange(false) },
                testTag = "all_appointments_tab",
            )
            FilterChipBox(
                text = "На Сегодня (${allAppointments.count { it.date == todayDateStr }})",
                isSelected = filterTodayOnly,
                accentColor = adminColor,
                onClick = { onFilterTodayOnlyChange(true) },
                testTag = "today_appointments_tab",
                leadingIcon = Icons.Default.Event,
            )
        }

        // Doctor filter chips
        Text(
            stringResource(R.string.dlg_filter_doctor),
            fontSize = AppFontSize.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            doctorsList.forEach { doc ->
                FilterChipBox(
                    text = doc,
                    isSelected = selectedDoctorFilter == doc,
                    accentColor = adminColor,
                    onClick = { onDoctorFilterChange(doc) },
                )
            }
        }

        // Status filter chips
        Text(
            stringResource(R.string.dlg_filter_status),
            fontSize = AppFontSize.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            statusesList.forEach { status ->
                val labelText = when (status) {
                    stringResource(R.string.dlg_all_statuses) -> stringResource(R.string.dlg_all_statuses)
                    "PENDING" -> stringResource(R.string.st_pending)
                    "APPROVED" -> stringResource(R.string.st_approved)
                    "COMPLETED" -> stringResource(R.string.st_completed)
                    "CANCELLED" -> stringResource(R.string.st_rejected)
                    else -> status
                }
                FilterChipBox(
                    text = labelText,
                    isSelected = selectedStatusFilter == status,
                    accentColor = adminColor,
                    onClick = { onStatusFilterChange(status) },
                )
            }
        }

        // Appointments list
        if (displayAppointments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.large))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.large))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.staff_no_sessions),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            displayAppointments.forEach { appt ->
                val isPendingSync = allPendingSyncs.any {
                    it.clientRequestId == appt.clientRequestId ||
                        (it.type == "UPDATE_STATUS" && it.payload.startsWith("${appt.id}|"))
                }
                StaffAppointmentCardItem(
                    appt = appt,
                    isPendingSync = isPendingSync,
                    onApprove = { onApprove(appt) },
                    onCancelClick = { onCancelClick(appt) },
                    onAddNotesClick = { onAddNotesClick(appt) },
                    onEditClick = { onEditClick(appt) },
                    onRegisterQueue = { onRegisterQueue(appt) },
                    accentColor = adminColor,
                )
            }
        }
    }
}

/**
 * High-6 audit fix: FilterChipBox — reusable filter chip used by
 * the doctor/status/today filters. Extracted to avoid repeating the
 * same Box + background + clickable pattern 15+ times.
 */
@Composable
private fun FilterChipBox(
    text: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.medium))
            .background(if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (leadingIcon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    fontSize = AppFontSize.caption,
                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = text,
                fontSize = AppFontSize.caption,
                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
