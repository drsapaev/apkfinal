package com.aistudio.clinicsystem.ui.screens.patient

import com.aistudio.clinicsystem.ui.theme.Spacing
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
import com.aistudio.clinicsystem.ui.viewmodel.PatientViewModel
import com.aistudio.clinicsystem.utils.TokenManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.aistudio.clinicsystem.R

@Composable
fun AppointmentCardItem(
    appointment: AppointmentEntity,
    isPendingSync: Boolean = false,
    onCancelClick: (String) -> Unit
) {
    val statusColor = when (appointment.status) {
        "PENDING" -> MaterialTheme.colorScheme.tertiary // Beautiful Amber
        "APPROVED" -> MaterialTheme.colorScheme.primary // Soft Forest Green
        "COMPLETED" -> MaterialTheme.colorScheme.primary // Ocean Blue
        "CANCELLED" -> MaterialTheme.colorScheme.error // Deep Coral Red
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (appointment.status) {
        "PENDING" -> stringResource(R.string.st_pending)
        "APPROVED" -> stringResource(R.string.st_approved_m)
        "COMPLETED" -> stringResource(R.string.st_completed_m)
        "CANCELLED" -> stringResource(R.string.st_cancelled)
        else -> appointment.status
    }

    val statusIcon = when (appointment.status) {
        "PENDING" -> Icons.Default.QueryBuilder
        "APPROVED" -> Icons.Default.TaskAlt
        "COMPLETED" -> Icons.Default.CheckCircle
        "CANCELLED" -> Icons.Default.Cancel
        else -> Icons.Default.Event
    }

    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.large))
    ) {
        // Stripe design layout
        Row(modifier = Modifier.fillMaxWidth()) {
            // Highlighting column badge stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(statusColor)
                    .align(Alignment.CenterVertically)
            )

            Column(modifier = Modifier.padding(Spacing.l).weight(1f)) {
                // Header of Appointment Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appointment.doctorName,
                            fontWeight = FontWeight.Bold,
                            fontSize = AppFontSize.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = appointment.specialty,
                            fontSize = AppFontSize.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Badge layout
                        Surface(
                            shape = RoundedCornerShape(Radius.medium),
                            color = statusColor.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = statusText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppFontSize.caption,
                                    color = statusColor
                                )
                            }
                        }

                        if (isPendingSync) {
                            Surface(
                                shape = RoundedCornerShape(Radius.medium),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = stringResource(R.string.st_syncing),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = AppFontSize.caption,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.m))

                // Date Time indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${appointment.date}  в  ${appointment.time}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = AppFontSize.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Reason representation
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = "Reason",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Симптомы: ${appointment.reason}",
                        fontSize = AppFontSize.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // If clinic comments / records exist
                if (appointment.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radius.small))
                            .padding(Spacing.s)
                    ) {
                        Row {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.ui_clinic_response),
                                    fontSize = AppFontSize.caption,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = appointment.notes,
                                    fontSize = AppFontSize.body,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Call to action: CANCEL APPOINTMENT if ACTIVE/PENDING
                if (appointment.status == "PENDING" || appointment.status == "APPROVED") {
                    Spacer(modifier = Modifier.height(Spacing.m))
                    TextButton(
                        onClick = { onCancelClick(appointment.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(Radius.small))
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_booking_button_${appointment.id}")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.pat_cancel_appointment), fontSize = AppFontSize.body, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
