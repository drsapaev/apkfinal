package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.data.db.UserEntity
import com.aistudio.clinicsystem.ui.theme.AppFontSize
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing

@Composable
fun TelegramBotCard(
    user: com.aistudio.clinicsystem.data.db.UserEntity?,
    onLinkClick: (String) -> Unit,
    onUnlinkClick: () -> Unit,
    onTestClick: () -> Unit,
) {
    var chatIdInput by remember { mutableStateOf("") }
    val telegramBlue = MaterialTheme.colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), // Pleasant light ice blue
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            // Header with custom styled paper plane icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = CircleShape,
                    color = telegramBlue,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Send, // Paper plane
                            contentDescription = "Telegram",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tg_bot_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "@IntellectClinicBot",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = telegramBlue,
                    )
                }
                if (user?.telegramChatId != null) {
                    Surface(
                        shape = RoundedCornerShape(Radius.small),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            text = stringResource(R.string.tg_linked),
                            fontSize = AppFontSize.caption,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.tg_bot_description),
                fontSize = AppFontSize.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 14.sp,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            if (user?.telegramChatId == null) {
                // Unlinked state UI
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        text = stringResource(R.string.tg_connect_instructions),
                        fontSize = AppFontSize.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        OutlinedTextField(
                            value = chatIdInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) chatIdInput = it },
                            placeholder = { Text(stringResource(R.string.tg_chat_id_placeholder)) },
                            singleLine = true,
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = telegramBlue,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier
                                    .weight(1.3f)
                                    .testTag("telegram_chat_id_input"),
                            shape = RoundedCornerShape(Radius.small),
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
                            modifier =
                                Modifier
                                    .weight(0.7f)
                                    .testTag("link_telegram_button"),
                        ) {
                            Text(
                                stringResource(R.string.ui_svyazat),
                                fontSize = AppFontSize.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.surface,
                            )
                        }
                    }
                }
            } else {
                // Linked state UI
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.tg_chat_id_label),
                                fontSize = AppFontSize.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = user.telegramChatId,
                                fontSize = AppFontSize.title,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        OutlinedButton(
                            onClick = onUnlinkClick,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(Radius.small),
                            modifier = Modifier.testTag("unlink_telegram_button"),
                        ) {
                            Text(stringResource(R.string.ui_otvyazat), fontSize = AppFontSize.caption, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onTestClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        shape = RoundedCornerShape(Radius.small),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("telegram_test_alert_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.tg_test_alert),
                            fontSize = AppFontSize.bodySmall,
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
