package com.aistudio.clinicsystem.ui.screens.staff

import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.aistudio.clinicsystem.data.db.AppointmentEntity
import com.aistudio.clinicsystem.data.db.MedicalRecordEntity
import com.aistudio.clinicsystem.data.db.QueueSnapshotEntity
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.data.db.PendingSyncEntity
import com.aistudio.clinicsystem.ui.viewmodel.StaffViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.aistudio.clinicsystem.R

@Composable
fun StaffAppointmentCardItem(
    appt: AppointmentEntity,
    isPendingSync: Boolean = false,
    onApprove: () -> Unit,
    onCancelClick: () -> Unit,
    onAddNotesClick: () -> Unit,
    onEditClick: () -> Unit,
    onRegisterQueue: () -> Unit,
    accentColor: Color
) {
    val statusColor = when (appt.status) {
        "PENDING" -> MaterialTheme.colorScheme.tertiary
        "APPROVED" -> MaterialTheme.colorScheme.primary
        "COMPLETED" -> MaterialTheme.colorScheme.primary
        "CANCELLED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusTextRu = when (appt.status) {
        "PENDING" -> "На рассмотрении"
        "APPROVED" -> "Подтверждено"
        "COMPLETED" -> "Осмотр завершен"
        "CANCELLED" -> "Отклонено"
        else -> appt.status
    }

    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.large))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = appt.patientName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Телефон: ${appt.patientPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.small),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = statusTextRu,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppFontSize.caption,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (isPendingSync) {
                        Surface(
                            shape = RoundedCornerShape(Radius.small),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "SyncRotate")
                                val rotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "rotation"
                                )
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Syncing",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .graphicsLayer { rotationZ = rotation }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ожидает...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppFontSize.caption,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Text(
                text = "Доктор: ${appt.doctorName}",
                fontSize = AppFontSize.body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Сеанс: ${appt.date} в ${appt.time}",
                fontSize = AppFontSize.body,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Жалобы: ${appt.reason}",
                fontSize = AppFontSize.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (appt.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.small))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Заметка / Ответ: ${appt.notes}",
                        fontSize = AppFontSize.body,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit button always active for registrar speed
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(Radius.small))
                        
                        .testTag("edit_appt_icon_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit appointment", tint = accentColor, modifier = Modifier.size(18.dp))
                }

                // Register in waiting room queue button: only show if approved
                if (appt.status == "APPROVED") {
                    Button(
                        onClick = onRegisterQueue,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(Radius.small),
                        modifier = Modifier.weight(1.2f).heightIn(min = 44.dp).testTag("register_queue_btn_appt")
                    ) {
                        Icon(Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("В очередь", fontSize = AppFontSize.bodySmall, color = MaterialTheme.colorScheme.surface)
                    }
                }

                if (appt.status == "PENDING") {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(Radius.small),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_odobrit), fontSize = AppFontSize.bodySmall, color = MaterialTheme.colorScheme.surface)
                    }

                    OutlinedButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(Radius.small),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_otklonit), fontSize = AppFontSize.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedButton(
                        onClick = onAddNotesClick,
                        shape = RoundedCornerShape(Radius.small),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = if (appt.status == "APPROVED") Modifier.weight(1f).heightIn(min = 44.dp) else Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.NoteAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_zametka), fontSize = AppFontSize.bodySmall)
                    }
                }
            }
        }
    }
}
