package com.aistudio.clinicsystem.ui.screens.staff

import com.aistudio.clinicsystem.ui.theme.Spacing
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity

/**
 * Section 0: Live Waiting Room Queue.
 *
 * P-02 refactor: extracted from StaffScreen.kt (was 1759 LOC). This composable
 * renders the waiting room queue with patient cards and management buttons
 * (move up/down, call to doctor, complete, remove).
 *
 * Caller is responsible for state hoisting (showRemoveQueueConfirmDialog).
 */
@Composable
fun StaffQueueSection(
    cachedQueueSnapshots: List<QueueSnapshotEntity>,
    adminColor: Color,
    onShiftQueuePosition: (String, Boolean) -> Unit,
    onUpdateQueueStatus: (String, String) -> Unit,
    onRemoveQueuePatient: (QueueSnapshotEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.large))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.large))
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QueuePlayNext,
                    contentDescription = null,
                    tint = adminColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    text = "Живая очередь в клинике",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.surface
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.small))
                    .background(adminColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Пациентов: ${cachedQueueSnapshots.size}",
                    fontSize = AppFontSize.bodySmall,
                    color = adminColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (cachedQueueSnapshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.medium))
                    .padding(Spacing.l),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "В коридоре ожидания пока никого нет.\nЗарегистрируйте подтвержденного пациента ниже.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = AppFontSize.body,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                cachedQueueSnapshots.sortedBy { it.position }.forEach { q ->
                    Card(
                        shape = RoundedCornerShape(Radius.medium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.m)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(adminColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${q.position}",
                                            color = MaterialTheme.colorScheme.surface,
                                            fontSize = AppFontSize.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(Spacing.s))
                                    Column {
                                        Text(
                                            text = q.patientName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AppFontSize.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Приём #${q.appointmentId}",
                                            fontSize = AppFontSize.caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val qStatusColor = when (q.status) {
                                    "WAITING" -> MaterialTheme.colorScheme.tertiary
                                    "IN_PROGRESS" -> MaterialTheme.colorScheme.primary
                                    "COMPLETED" -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val qStatusText = when (q.status) {
                                    "WAITING" -> "Ожидает"
                                    "IN_PROGRESS" -> "На приёме"
                                    "COMPLETED" -> "Осмотрен"
                                    else -> q.status
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.small))
                                        .background(qStatusColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = qStatusText,
                                        fontSize = AppFontSize.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = qStatusColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.s))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onShiftQueuePosition(q.id, true) },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.small))
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.ui_up), modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onShiftQueuePosition(q.id, false) },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.small))
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.ui_down), modifier = Modifier.size(16.dp))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                if (q.status == "WAITING") {
                                    Button(
                                        onClick = { onUpdateQueueStatus(q.id, "IN_PROGRESS") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text(stringResource(R.string.ui_call_to_doctor), fontSize = AppFontSize.caption, color = MaterialTheme.colorScheme.surface)
                                    }
                                } else if (q.status == "IN_PROGRESS") {
                                    Button(
                                        onClick = { onUpdateQueueStatus(q.id, "COMPLETED") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text("Завершить прием", fontSize = AppFontSize.caption, color = MaterialTheme.colorScheme.surface)
                                    }
                                }

                                TextButton(
                                    onClick = { onRemoveQueuePatient(q) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.heightIn(min = 44.dp).testTag("remove_queue_patient_button")
                                ) {
                                    Text(stringResource(R.string.ui_ubrat), fontSize = AppFontSize.caption)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
