package com.aistudio.clinicsystem.ui.screens.patient

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
        "PENDING" -> "На рассмотрении"
        "APPROVED" -> "Подтверждён"
        "COMPLETED" -> "Осмотр завершён"
        "CANCELLED" -> "Отменён"
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
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

            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
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
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = appointment.specialty,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Badge layout
                        Surface(
                            shape = RoundedCornerShape(12.dp),
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
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = statusColor
                                )
                            }
                        }

                        if (isPendingSync) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
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
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ожидает...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        fontSize = 13.sp,
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
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Симптомы: ${appointment.reason}",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }

                // If clinic comments / records exist
                if (appointment.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(8.dp)
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
                                    text = "Ответ клиники:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = appointment.notes,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Call to action: CANCEL APPOINTMENT if ACTIVE/PENDING
                if (appointment.status == "PENDING" || appointment.status == "APPROVED") {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { onCancelClick(appointment.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_booking_button_${appointment.id}")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отменить запись на приём", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
