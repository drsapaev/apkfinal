package com.aistudio.clinicsystem.ui.screens.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.domain.model.PatientQueuePosition
import com.aistudio.clinicsystem.domain.model.PatientQueueUiState
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TASK-6: the patient's REAL live queue positions from
 * GET /api/v1/mobile/queues/my-position — doctor, my number, currently
 * serving number, people ahead, estimated wait, and the fetch timestamp.
 *
 * The state distinguishes the cases the UI must not confuse:
 *   - fresh positions  → normal rendering;
 *   - empty (success)  → "вы не в очереди" (old positions were cleared);
 *   - stale (error)    → last known data + explicit warning;
 *   - loading          → progress note.
 */
@Composable
fun PatientLiveQueueCard(
    state: PatientQueueUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(Radius.large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    text = "Моя очередь",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить очередь",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            when {
                state.isLoading -> {
                    Text(
                        text = "Обновление позиции…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.positions.isEmpty() && !state.isStale -> {
                    // A successful empty response — no active queues.
                    Text(
                        text = "Сейчас вы не стоите в очереди.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.positions.isEmpty() && state.isStale -> {
                    Text(
                        text = "Нет активных позиций (не удалось обновить: сеть недоступна).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    if (state.isStale) {
                        Text(
                            text = "Данные могли устареть — не удалось обновить (сеть недоступна).",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(Spacing.s))
                    }
                    state.positions.forEach { position ->
                        PatientQueuePositionRow(position)
                        Spacer(modifier = Modifier.height(Spacing.s))
                    }
                    Text(
                        text =
                            "Обновлено: " +
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(state.lastUpdated)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientQueuePositionRow(position: PatientQueuePosition) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.ConfirmationNumber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = position.doctorName.ifBlank { "Очередь" } +
                    if (position.specialty.isNotBlank()) " · ${position.specialty}" else "",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "Мой номер ${position.myNumber} · текущий ${position.currentNumber}" +
                        " · впереди ${position.patientsBeforeMe}" +
                        " · ожидание ~${position.estimatedWaitMinutes} мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .background(statusColor(position.status), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText(position.status),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private fun statusText(status: String): String =
    when (status.lowercase()) {
        "waiting" -> "Ожидание"
        "ready" -> "Вас вызывают"
        else -> status
    }

@Composable
private fun statusColor(status: String): androidx.compose.ui.graphics.Color =
    when (status.lowercase()) {
        "ready" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
        "waiting" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
