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
fun HeaderGreetingBanner(
    userName: String,
    activeAppointmentsCount: Int,
    completedRecordsCount: Int
) {
    val localTime = Calendar.getInstance()
    val hour = localTime.get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour in 5..11 -> "Доброе утро"
        hour in 12..16 -> "Добрый день"
        hour in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    Card(
        shape = RoundedCornerShape(Radius.large),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.large)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                    )
                )
                .padding(Spacing.l)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "$greeting,",
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            fontSize = AppFontSize.title,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$userName!",
                            color = MaterialTheme.colorScheme.surface,
                            fontSize = AppFontSize.display,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 28.sp
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))
                HorizontalDivider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(Spacing.m))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.l)
                ) {
                    // Item 1: Upcoming appointments
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.small),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Активные записи",
                                fontSize = AppFontSize.caption,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "$activeAppointmentsCount",
                                fontSize = AppFontSize.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface
                            )
                        }
                    }

                    // Item 2: Records
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.small),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Медицинская карта",
                                fontSize = AppFontSize.caption,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                            Text(
                                // P-29 fix: use plurals for grammatical correctness
                                text = "${completedRecordsCount} " +
                                    androidx.compose.ui.platform.LocalContext.current.resources
                                        .getQuantityString(
                                            com.aistudio.clinicsystem.R.plurals.record_count,
                                            completedRecordsCount,
                                            completedRecordsCount
                                        ),
                                fontSize = AppFontSize.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }
    }
}
