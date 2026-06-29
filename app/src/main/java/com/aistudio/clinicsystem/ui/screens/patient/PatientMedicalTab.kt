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
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity

/**
 * P-03 refactor: Medical tab content for PatientScreen Bottom Navigation.
 *
 * Shows: medical reports section with search and expand/collapse.
 */
@Composable
fun PatientMedicalTab(
    records: List<MedicalRecordEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    expandedRecords: Set<String>,
    isFetching: Boolean,
    onFetchClick: () -> Unit,
    onRecordToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredRecords = if (searchQuery.isBlank()) {
        records
    } else {
        records.filter {
            it.diagnosis.contains(searchQuery, ignoreCase = true) ||
                    it.doctorName.contains(searchQuery, ignoreCase = true) ||
                    it.prescription.contains(searchQuery, ignoreCase = true) ||
                    it.recommendations.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MedicalReportsSection(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            records = filteredRecords,
            expandedRecords = expandedRecords,
            isFetching = isFetching,
            onFetchClick = onFetchClick,
            onRecordToggle = onRecordToggle
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
