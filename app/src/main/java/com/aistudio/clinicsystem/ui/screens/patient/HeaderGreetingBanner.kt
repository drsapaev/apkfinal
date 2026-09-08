package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing
import java.util.Calendar

@Composable
fun HeaderGreetingBanner(
    userName: String,
    activeAppointmentsCount: Int,
    completedRecordsCount: Int,
) {
    val localTime = Calendar.getInstance()
    val hour = localTime.get(Calendar.HOUR_OF_DAY)
    val greeting =
        when {
            hour in 5..11 -> stringResource(R.string.g_morning)
            hour in 12..16 -> stringResource(R.string.g_day)
            hour in 17..22 -> stringResource(R.string.g_evening)
            else -> stringResource(R.string.g_night)
        }

    Card(
        shape = RoundedCornerShape(Radius.large),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.large)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
                        ),
                    ).padding(Spacing.l),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(
                            text = "$greeting,",
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            fontSize = AppFontSize.title,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "$userName!",
                            color = MaterialTheme.colorScheme.surface,
                            fontSize = AppFontSize.display,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 28.sp,
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        modifier = Modifier.size(50.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))
                HorizontalDivider(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(Spacing.m))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    // Item 1: Upcoming appointments
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.small),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.pat_active_records),
                                fontSize = AppFontSize.caption,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            )
                            Text(
                                text = "$activeAppointmentsCount",
                                fontSize = AppFontSize.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }

                    // Item 2: Records
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.small),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.pat_medical_card),
                                fontSize = AppFontSize.caption,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            )
                            Text(
                                // P-29 fix: use plurals for grammatical correctness
                                text =
                                    "$completedRecordsCount " +
                                        androidx.compose.ui.platform.LocalContext.current.resources
                                            .getQuantityString(
                                                com.aistudio.clinicsystem.R.plurals.record_count,
                                                completedRecordsCount,
                                                completedRecordsCount,
                                            ),
                                fontSize = AppFontSize.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
                }
            }
        }
    }
}
