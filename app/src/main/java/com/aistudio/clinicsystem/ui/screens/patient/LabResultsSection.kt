package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.LabResultEntity
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing

/**
 * Stage 6: LabResultsSection — displays lab results from lab_results table.
 *
 * Previously, lab results were mixed into MedicalReportsSection via
 * MedicalRecordEntity (with testName→diagnosis mapping). Now they have
 * their own section with proper field names (test name, result, unit,
 * reference range, status, performed date, doctor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabResultsSection(
    labResults: List<LabResultEntity>,
    isFetching: Boolean,
    onFetchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.l),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.lab_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = onFetchClick,
                    enabled = !isFetching,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.lab_results_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            if (labResults.isEmpty()) {
                Text(
                    text = stringResource(R.string.lab_results_empty),
                    fontSize = AppFontSize.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    items(labResults, key = { it.id }) { result ->
                        LabResultCard(result = result)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabResultCard(result: LabResultEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.m),
        ) {
            // Test name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = result.testName,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppFontSize.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(result.status, fontSize = AppFontSize.caption) },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Result + unit
            if (!result.result.isNullOrBlank()) {
                Text(
                    text = stringResource(
                        R.string.lab_result_value,
                        result.result,
                        result.unit?.let { " $it" } ?: ""
                    ),
                    fontSize = AppFontSize.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Reference range
            if (!result.referenceRange.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.lab_result_reference, result.referenceRange),
                    fontSize = AppFontSize.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Performed date + doctor
            if (!result.performedAt.isNullOrBlank()) {
                Text(
                    text = stringResource(
                        R.string.lab_result_date,
                        result.performedAt,
                        result.doctorName?.let { " — $it" } ?: ""
                    ),
                    fontSize = AppFontSize.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
