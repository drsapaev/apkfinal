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
fun TelegramBotCard(
    user: com.aistudio.clinicsystem.data.db.UserEntity?,
    onLinkClick: (String) -> Unit,
    onUnlinkClick: () -> Unit,
    onTestClick: () -> Unit
) {
    var chatIdInput by remember { mutableStateOf("") }
    val telegramBlue = MaterialTheme.colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), // Pleasant light ice blue
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            // Header with custom styled paper plane icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = telegramBlue,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Send, // Paper plane
                            contentDescription = "Telegram",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Telegram-Бот Оповещений",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "@IntellectClinicBot",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = telegramBlue
                    )
                }
                if (user?.telegramChatId != null) {
                    Surface(
                        shape = RoundedCornerShape(Radius.small),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "СВЯЗАНО ✔",
                            fontSize = AppFontSize.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Бот автоматически информирует об изменениях статуса записей на прием и моментально доставляет назначения врачей.",
                fontSize = AppFontSize.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 14.sp
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            if (user?.telegramChatId == null) {
                // Unlinked state UI
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        text = "Для подключения бота запустите @IntellectClinicBot в Telegram и введите ваш Chat ID сюда:",
                        fontSize = AppFontSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        OutlinedTextField(
                            value = chatIdInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) chatIdInput = it },
                            placeholder = { Text("ID чата (например: 5040112)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("telegram_chat_id_input"),
                            shape = RoundedCornerShape(Radius.small)
                        )

                        Button(
                            onClick = {
                                if (chatIdInput.isNotBlank()) {
                                    onLinkClick(chatIdInput)
                                    chatIdInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            shape = RoundedCornerShape(Radius.small),
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("link_telegram_button")
                        ) {
                            Text(stringResource(R.string.ui_svyazat), fontSize = AppFontSize.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            } else {
                // Linked state UI
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Идентификатор Чата (Chat ID):",
                                fontSize = AppFontSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = user.telegramChatId,
                                fontSize = AppFontSize.title,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedButton(
                            onClick = onUnlinkClick,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(Radius.small),
                            modifier = Modifier.testTag("unlink_telegram_button")
                        ) {
                            Text(stringResource(R.string.ui_otvyazat), fontSize = AppFontSize.caption, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onTestClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        shape = RoundedCornerShape(Radius.small),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_test_alert_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Проверить доставку (Тестовое оповещение)", fontSize = AppFontSize.bodySmall, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
