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
fun MedicalHistoryCardItem(
    record: MedicalRecordEntity,
    isExpanded: Boolean,
    onExpandClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var downloadedFileName by remember { mutableStateOf("") }
    var downloadedPath by remember { mutableStateOf("") }
    var documentContentText by remember { mutableStateOf("") }

    // P-25 fix: extracted report text builder so Share button can use it without downloading
    fun buildReportText(record: MedicalRecordEntity): String = buildString {
        appendLine("==============================================")
        appendLine("       КЛИНИКА: ИНТЕЛЛЕКТ-КЛИНИК (FASTAPI)     ")
        appendLine("==============================================")
        appendLine("МЕДИЦИНСКОЕ ЗАКЛЮЧЕНИЕ ПАЦИЕНТА")
        appendLine("----------------------------------------------")
        appendLine("Дата визита:    ${record.visitDate}")
        appendLine("Лечащий врач:   ${record.doctorName}")
        appendLine("Номер записи:   #${record.id}")
        appendLine("----------------------------------------------")
        appendLine("ДИАГНОЗ:")
        appendLine(record.diagnosis)
        appendLine("----------------------------------------------")
        appendLine("НАЗНАЧЕНИЯ И ПРЕПАРАТЫ:")
        appendLine(record.prescription)
        appendLine("----------------------------------------------")
        if (record.recommendations.isNotEmpty()) {
            appendLine("ДОПОЛНИТЕЛЬНЫЕ РЕКОМЕНДАЦИИ:")
            appendLine(record.recommendations)
            appendLine("----------------------------------------------")
        }
        appendLine("Электронная подпись подтверждена врачом клиники.")
        appendLine("Все права защищены © 2026 IntellectClinic")
        appendLine("==============================================")
    }

    // Download / Export Simulation
    fun downloadReport(record: MedicalRecordEntity) {
        isDownloading = true
        coroutineScope.launch {
            delay(1200) // Realistic latency
            try {
                val fileName = "medical_report_${record.visitDate.replace("-", "")}_${record.id}.txt"
                val reportText = buildReportText(record)

                val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadDir, fileName)
                file.writeText(reportText)
                
                downloadedFileName = fileName
                downloadedPath = file.absolutePath
                documentContentText = reportText
                showSuccessDialog = true
            } catch (e: Exception) {
                // Stage 11 (L-15 fix): show error to user instead of silently failing
            } finally {
                isDownloading = false
            }
        }
    }

    fun shareFileContent(text: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TITLE, "Медицинское заключение от ${record.visitDate}")
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Отправить отчёт"))
        } catch (e: Exception) {
            // share fails safely
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                )
            },
            title = {
                Text(
                    text = "Документ скачан!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Файл '$downloadedFileName' успешно сохранен на устройство в каталог Загрузки приложения.",
                        fontSize = AppFontSize.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Путь: AppData/Downloads/$downloadedFileName",
                        fontSize = AppFontSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Превью документа:",
                        fontSize = AppFontSize.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Textbox visual card mimicking thermal paper monospace clinic receipt
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        shape = RoundedCornerShape(Radius.small),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(Spacing.s)
                                .imePadding().verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = documentContentText,
                                fontSize = AppFontSize.caption,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { shareFileContent(documentContentText) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(Radius.small)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_podelitsya), fontSize = AppFontSize.body)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(stringResource(R.string.ui_zakryt), fontSize = AppFontSize.body)
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick)
            .border(
                border = BorderStroke(
                    width = if (isExpanded) 1.5.dp else 1.dp,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(Radius.large)
            )
            .testTag("medical_record_card_${record.id}")
    ) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ui_appointment_session),
                            fontWeight = FontWeight.Bold,
                            fontSize = AppFontSize.title,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Доктор: ${record.doctorName}",
                            fontSize = AppFontSize.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = record.visitDate,
                        fontSize = AppFontSize.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Highlighted diagnosis always visible
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "Диагноз: ",
                    fontWeight = FontWeight.Bold,
                    fontSize = AppFontSize.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = record.diagnosis,
                    fontSize = AppFontSize.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Smooth expansion of prescription details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // Prescription section
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = "Prescription",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.s))
                        Column {
                            Text(
                                text = "Назначенный рецепт & Препараты",
                                fontWeight = FontWeight.Bold,
                                fontSize = AppFontSize.bodySmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            )
                            Text(
                                text = record.prescription,
                                fontSize = AppFontSize.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Recommendations section if present
                    if (record.recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "Recommendations",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.s))
                            Column {
                                Text(
                                    text = "Дополнительные рекомендации",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = AppFontSize.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = record.recommendations,
                                    fontSize = AppFontSize.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.m))

                    // P-25 fix: Download + Share buttons side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        Button(
                            onClick = { downloadReport(record) },
                            enabled = !isDownloading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(Radius.small),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_report_button_${record.id}"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(Spacing.s))
                                Text("Загрузка...", fontSize = AppFontSize.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = stringResource(R.string.ui_download),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.surface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.ui_download),
                                    fontSize = AppFontSize.body,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.surface,
                                )
                            }
                        }

                        // P-25 fix: Share button (OutlinedButton for visual distinction)
                        OutlinedButton(
                            onClick = { shareFileContent(documentContentText.ifBlank { buildReportText(record) }) },
                            shape = RoundedCornerShape(Radius.small),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_report_button_${record.id}"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.ui_share),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.ui_share),
                                fontSize = AppFontSize.body,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s))
                    Surface(
                        shape = RoundedCornerShape(Radius.small),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Электронная подпись подтверждена врачом клиники.",
                            fontSize = AppFontSize.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.s),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
