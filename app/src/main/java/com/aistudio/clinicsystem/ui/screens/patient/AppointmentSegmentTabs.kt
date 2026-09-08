package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentSegmentTabs(
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        listOf(
            Triple("ALL", stringResource(R.string.dlg_all_appointments), "appointment_tab_all"),
            Triple("ACTIVE", stringResource(R.string.appt_filter_active), "appointment_tab_active"),
            Triple("FINISHED", stringResource(R.string.appt_filter_finished), "appointment_tab_finished"),
        ).forEach { (filter, title, tag) ->
            val isSelected = selectedFilter == filter
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.small))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onFilterSelect(filter) }
                        .padding(vertical = 8.dp)
                        .testTag(tag),
            ) {
                Text(
                    text = title,
                    fontSize = AppFontSize.body,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
