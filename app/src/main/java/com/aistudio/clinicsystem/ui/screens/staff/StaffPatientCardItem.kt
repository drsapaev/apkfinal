package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing

@Composable
fun StaffPatientCardItem(
    patient: UserEntity,
    recordsCount: Int,
    onWriteRecord: () -> Unit,
    accentColor: Color,
) {
    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.large)),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(Spacing.l)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalInformation,
                    contentDescription = null,
                    tint = accentColor,
                )
            }

            Spacer(modifier = Modifier.width(Spacing.m))

            // P-23 fix: mergeDescendants so TalkBack announces patient info as one group
            val a11yDescription =
                stringResource(
                    R.string.staff_patient_a11y_description,
                    patient.fullName,
                    patient.phone,
                    recordsCount,
                )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            contentDescription = a11yDescription
                        },
            ) {
                Text(
                    text = patient.fullName,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppFontSize.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.phone_label_short, patient.phone),
                    fontSize = AppFontSize.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.records_count_label, recordsCount),
                    fontSize = AppFontSize.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            IconButton(onClick = onWriteRecord) {
                Icon(
                    imageVector = Icons.Default.AddBox,
                    contentDescription = "New Record entry",
                    tint = accentColor,
                    modifier = Modifier,
                )
            }
        }
    }
}
