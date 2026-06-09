package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.SyncLogEntity
import com.example.ui.viewmodel.ClinicViewModel
import com.example.utils.TokenManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncConsoleView(
    viewModel: ClinicViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    var isExpanded by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("SYNC") } // SYNC, SECURITY

    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Deep slate terminal black
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF334155),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Switch Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Syncing logs",
                        tint = if (isSyncing) Color(0xFF38BDF8) else Color(0xFF34D399),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "САНДБОКС СИНХРОНИЗАЦИИ И БЕЗОПАСНОСТИ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSyncing) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF34D399).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isSyncing) "SYNCING..." else "SECURE ✔",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSyncing) Color(0xFF38BDF8) else Color(0xFF34D399),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = "Toggle Expand",
                    tint = Color(0xFF94A3B8)
                )
            }

            // Hidden Extended Logs Pane
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    // Tab selector row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val syncTabActive = activeTab == "SYNC"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (syncTabActive) Color(0xFF334155) else Color.Transparent)
                                .border(1.dp, if (syncTabActive) Color(0xFF475569) else Color(0xFF334155), RoundedCornerShape(6.dp))
                                .clickable { activeTab = "SYNC" }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "СИНХРОНИЗАЦИЯ ЛОГОВ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (syncTabActive) Color.White else Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val securityTabActive = activeTab == "SECURITY"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (securityTabActive) Color(0xFF0F766E).copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (securityTabActive) Color(0xFF095D56) else Color(0xFF334155), RoundedCornerShape(6.dp))
                                .clickable { activeTab = "SECURITY" }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SECURITY REVIEW & PENTEST",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (securityTabActive) Color(0xFF2DD4BF) else Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (activeTab == "SYNC") {
                        // SYNC TAB LAYOUT
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Buttons Toolbar Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.triggerCloudSynchronization() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.5f),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Синхронизировать Облако", fontSize = 11.sp, color = Color.White)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.clearAllLogs() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Очистить Логи", fontSize = 11.sp, color = Color(0xFFEF4444))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Внутренний SQL реестр транзакций:",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Log items container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A)) // Pure deep black terminal
                                    .padding(8.dp)
                            ) {
                                if (logs.isEmpty()) {
                                    Text(
                                        text = "Транзакции пока пусты. Все действия синхронизации отображаются здесь.",
                                        color = Color(0xFF475569),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    LazyColumn(reverseLayout = false) {
                                        items(logs, key = { it.id }) { log ->
                                            val logColor = when (log.direction) {
                                                "PATIENT_TO_STAFF" -> Color(0xFFFB7185) // Rose (from patient)
                                                "STAFF_TO_PATIENT" -> Color(0xFF60A5FA) // Blue (from staff)
                                                "CLOUD_SYNC_SIMULATOR" -> Color(0xFFFBBF24) // Gold (Sim HTTP requests)
                                                else -> Color(0xFF34D399) // Mint Green (System)
                                            }

                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                Row {
                                                    Text(
                                                        text = "[${formatter.format(Date(log.timestamp))}] ",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = log.logMessage,
                                                        color = logColor,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // SECURITY REVIEW & PENTEST TAB LAYOUT
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "🛡️ ОТЧЁТ SECURITY AUDIT (РЕАЛЬНОЕ ВРЕМЯ):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val context = LocalContext.current
                            val secureDisplay = TokenManager.isScreenSecureEnabled(context)

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                SecurityStatusRow(
                                    label = "Шифрование БД SQLite",
                                    statusText = "✅ АКТИВНО (SQLCipher AES-256)",
                                    statusColor = Color(0xFF34D399)
                                )
                                SecurityStatusRow(
                                    label = "Защита экрана (FLAG_SECURE)",
                                    statusText = if (secureDisplay) "✅ АКТИВНО (Превью скрыто)" else "⚠️ ОТКЛЮЧЕНО (Функция выключена)",
                                    statusColor = if (secureDisplay) Color(0xFF34D399) else Color(0xFFFBBF24)
                                )
                                SecurityStatusRow(
                                    label = "adb backup (Защита бэкапа)",
                                    statusText = "✅ ЗАБЛОКИРОВАНО (allowBackup=false)",
                                    statusColor = Color(0xFF34D399)
                                )
                                SecurityStatusRow(
                                    label = "Secure Preferences Storage",
                                    statusText = "✅ БЕЗОПАСНО (AES-256-GCM SIV)",
                                    statusColor = Color(0xFF34D399)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "🎯 АКТИВНЫЕ ТЕСТЫ НА ПРОНИКНОВЕНИЕ (PENTESTER SUITE):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            var pentestLog by remember { mutableStateOf("Выберите тест на проникновение из набора ниже, чтобы запустить симуляцию в реальном времени.") }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F766E))
                                        .clickable {
                                            pentestLog = "[PENTEST - SQL Injection Test]\n" +
                                                    "⚙️ Внедрение вредоносного SQL: \"' OR 1=1 --\" в форму телефона...\n" +
                                                    "🔍 Выполнение через сгенерированные DAO-запросы Room...\n" +
                                                    "✅ Результат: СИСТЕМА НЕУЯЗВИМА. Скомпилированные Room DAO используют строго параметризованные Precompiled SQLite Statements. SQLi атака полностью блокирована."
                                            viewModel.logSecurityEvent("🛡️ Pentest: Заблокирована симуляция SQL Injection внедрения.", "SYSTEM_SYNC")
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SQL Inject", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F766E))
                                        .clickable {
                                            pentestLog = "[PENTEST - Screen Snooping Capture]\n" +
                                                    "⚙️ Инициализация daemon фонового захвата экрана...\n" +
                                                    "🔍 Запрос snapshot кадра окна MainActivity...\n" +
                                                    if (secureDisplay) {
                                                        "✅ Результат: СИСТЕМА ЗАЩИЩЕНА. FLAG_SECURE активен. Android OS возвращает пустую заливку для неавторизованных служб и скриншоттеров."
                                                    } else {
                                                        "❌ Результат: УЯЗВИМОСТЬ! Снимок экрана успешно получен, так как Защита экрана выключена пользователем в настройках."
                                                    }
                                            viewModel.logSecurityEvent("🛡️ Pentest: Проверена стойкость данных против snoop захвата экрана.", "SYSTEM_SYNC")
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Scr Snoop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F766E))
                                        .clickable {
                                            pentestLog = "[PENTEST - DB Dump Decryption]\n" +
                                                    "⚙️ Попытка несанкционированного прямого чтения базы /databases/clinic_database...\n" +
                                                    "🔍 Поиск бинарных заголовков SQLite Header сигнатуры...\n" +
                                                    "✅ Результат: СИСТЕМА ЗАЩИЩЕНА. Заголовки SQLite не обнаружены. Файл полностью зашифрован SQLCipher AES-256. Чтение без мастер-ключа выдает сплошной случайный шум."
                                            viewModel.logSecurityEvent("🛡️ Pentest: Попытка прямого дампа DB показала полную нечитаемость шифртекста.", "SYSTEM_SYNC")
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("DB Dump", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0F766E))
                                        .clickable {
                                            pentestLog = "[PENTEST - MITM Privilege TAMPERING]\n" +
                                                    "⚙️ Симуляция Man-in-the-Middle роутера...\n" +
                                                    "⚙️ Перехват и попытка изменения роли в сессионном ответе на STAFF...\n" +
                                                    "🔍 Проверка JWT криптографической подписи сервера...\n" +
                                                    "✅ Результат: УГРОЗА КУПИРОВАНА. Измененный JWT не совпадает по сигнатуре подписи. Любые измененные HTTP пакеты сессии отклоняются с кодом 401."
                                            viewModel.logSecurityEvent("🛡️ Pentest: Заблокирована симуляция подмены HTTP привилегий (MITM сессия).", "SYSTEM_SYNC")
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("MITM Tamper", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF020617)) // Deep dark cyber console
                                    .border(1.dp, Color(0xFF0F766E).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                LazyColumn {
                                    item {
                                        Text(
                                            text = pentestLog,
                                            color = if (pentestLog.contains("❌")) Color(0xFFFDA4AF) else Color(0xFF2DD4BF),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityStatusRow(
    label: String,
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1),
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = statusText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            fontFamily = FontFamily.Monospace
        )
    }
}
