package com.example.ui.screens

import androidx.compose.animation.*
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
import com.example.data.db.AppointmentEntity
import com.example.data.db.MedicalRecordEntity
import com.example.ui.viewmodel.PatientViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientScreen(
    viewModel: PatientViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val appointments by viewModel.patientAppointments.collectAsStateWithLifecycle()
    val records by viewModel.patientRecords.collectAsStateWithLifecycle()
    val isFetchingRecords by viewModel.isFetchingReports.collectAsStateWithLifecycle()

    var showEditProfile by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf(currentUser?.fullName ?: "") }

    var showBookDialog by remember { mutableStateOf(false) }
    var selectedDoctor by remember { mutableStateOf("Dr. Rustam Sapaev") }
    var selectedSpecialty by remember { mutableStateOf("Стоматолог-Хирург") }
    var selectedDateIdx by remember { mutableStateOf(0) }
    var selectedTimeSlot by remember { mutableStateOf("11:00") }
    var bookingReasonInput by remember { mutableStateOf("") }

    // Navigation and filters
    var selectedAppFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, FINISHED
    var medicalSearchQuery by remember { mutableStateOf("") }
    var expandedRecords by remember { mutableStateOf(setOf<Int>()) }

    val tealPrimary = MaterialTheme.colorScheme.primary
    val tealLight = MaterialTheme.colorScheme.primaryContainer
    val tealDark = MaterialTheme.colorScheme.secondary
    val accentNavy = MaterialTheme.colorScheme.onSurface
    val backgroundSoft = MaterialTheme.colorScheme.background

    // Generate upcoming dates for booking
    val bookingDatesList = remember {
        val list = mutableListOf<String>()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        repeat(5) {
            list.add(formatter.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val doctors = listOf(
        Pair("Dr. Rustam Sapaev", "Стоматолог-Хирург (Dentist-Surgeon)"),
        Pair("Dr. Elena Petrova", "Кардиолог (Cardiologist)"),
        Pair("Dr. Alexander Smirnov", "Невролог (Neurologist)")
    )
    val timeSlots = listOf("09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00")

    // Filter appointments based on selection
    val filteredAppointments = remember(appointments, selectedAppFilter) {
        when (selectedAppFilter) {
            "ACTIVE" -> appointments.filter { it.status == "PENDING" || it.status == "APPROVED" }
            "FINISHED" -> appointments.filter { it.status == "COMPLETED" || it.status == "CANCELLED" }
            else -> appointments
        }
    }

    // Filter records based on search query
    val filteredRecords = remember(records, medicalSearchQuery) {
        if (medicalSearchQuery.isBlank()) {
            records
        } else {
            records.filter {
                it.diagnosis.contains(medicalSearchQuery, ignoreCase = true) ||
                        it.doctorName.contains(medicalSearchQuery, ignoreCase = true) ||
                        it.prescription.contains(medicalSearchQuery, ignoreCase = true) ||
                        it.recommendations.contains(medicalSearchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Интеллект-Клиника",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Личный Кабинет Пациента",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
                    val themeIcon = when (themeMode) {
                        "LIGHT" -> Icons.Default.LightMode
                        "DARK" -> Icons.Default.DarkMode
                        else -> Icons.Default.BrightnessAuto
                    }
                    val themeDescription = when (themeMode) {
                        "LIGHT" -> "Светлая тема"
                        "DARK" -> "Темная тема"
                        else -> "Системная тема"
                    }

                    IconButton(
                        onClick = {
                            val nextMode = when (themeMode) {
                                "SYSTEM" -> "LIGHT"
                                "LIGHT" -> "DARK"
                                else -> "SYSTEM"
                            }
                            viewModel.setThemeMode(nextMode)
                        },
                        modifier = Modifier
                            .testTag("theme_toggle_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = themeDescription,
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { viewModel.logOut() },
                        modifier = Modifier
                            .testTag("logout_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log out",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tealPrimary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBookDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Book Appointment") },
                text = { Text("Запись на приём", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF00BFA5),
                contentColor = Color.White,
                modifier = Modifier
                    .testTag("book_appointment_fab")
                    .padding(bottom = 12.dp)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundSoft)
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth > 720.dp

            if (isWideScreen) {
                // RESPONSIVE LANDSCAPE TABLET LAYOUT: Two independent scrollable panes
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // LEFT COLUMN (60% width): Greeting banner + Appointments
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HeaderGreetingBanner(
                            userName = currentUser?.fullName ?: "Пациент",
                            activeAppointmentsCount = appointments.count { it.status == "PENDING" || it.status == "APPROVED" },
                            completedRecordsCount = records.size
                        )

                        // Segment selector for Appointments
                        AppointmentSegmentTabs(
                            selectedFilter = selectedAppFilter,
                            onFilterSelect = { selectedAppFilter = it }
                        )

                        AppointmentsSessionList(
                            appointments = filteredAppointments,
                            onCancelClick = { id -> viewModel.cancelAppointment(id, "Отменено пациентом в кабинете") }
                        )
                    }

                    // RIGHT COLUMN (40% width): Profile Details + Past Medical Records
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileCabinetCard(
                            user = currentUser,
                            onEditClick = {
                                editNameInput = currentUser?.fullName ?: ""
                                showEditProfile = true
                            },
                            onBiometricToggle = { viewModel.setBiometricEnrollment(it) }
                        )

                        TelegramBotCard(
                            user = currentUser,
                            onLinkClick = { chat -> viewModel.linkTelegramChatId(chat) },
                            onUnlinkClick = { viewModel.unlinkTelegramChatId() },
                            onTestClick = { viewModel.sendTestTelegramNotification() }
                        )

                        MedicalReportsSection(
                            searchQuery = medicalSearchQuery,
                            onSearchQueryChange = { medicalSearchQuery = it },
                            records = filteredRecords,
                            expandedRecords = expandedRecords,
                            isFetching = isFetchingRecords,
                            onFetchClick = { viewModel.fetchMedicalReports() },
                            onRecordToggle = { id ->
                                expandedRecords = if (expandedRecords.contains(id)) {
                                    expandedRecords - id
                                } else {
                                    expandedRecords + id
                                }
                            }
                        )
                    }
                }
            } else {
                // RESPONSIVE PORTRAIT MOBILE LAYOUT: Full-bleed vertical scroll
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderGreetingBanner(
                        userName = currentUser?.fullName ?: "Пациент",
                        activeAppointmentsCount = appointments.count { it.status == "PENDING" || it.status == "APPROVED" },
                        completedRecordsCount = records.size
                    )

                    ProfileCabinetCard(
                        user = currentUser,
                        onEditClick = {
                            editNameInput = currentUser?.fullName ?: ""
                            showEditProfile = true
                        },
                        onBiometricToggle = { viewModel.setBiometricEnrollment(it) }
                    )

                    TelegramBotCard(
                        user = currentUser,
                        onLinkClick = { chat -> viewModel.linkTelegramChatId(chat) },
                        onUnlinkClick = { viewModel.unlinkTelegramChatId() },
                        onTestClick = { viewModel.sendTestTelegramNotification() }
                    )

                    // Tab bar for Appointments
                    AppointmentSegmentTabs(
                        selectedFilter = selectedAppFilter,
                        onFilterSelect = { selectedAppFilter = it }
                    )

                    AppointmentsSessionList(
                        appointments = filteredAppointments,
                        onCancelClick = { id -> viewModel.cancelAppointment(id, "Отменено пациентом в кабинете") }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    MedicalReportsSection(
                        searchQuery = medicalSearchQuery,
                        onSearchQueryChange = { medicalSearchQuery = it },
                        records = filteredRecords,
                        expandedRecords = expandedRecords,
                        isFetching = isFetchingRecords,
                        onFetchClick = { viewModel.fetchMedicalReports() },
                        onRecordToggle = { id ->
                            expandedRecords = if (expandedRecords.contains(id)) {
                                expandedRecords - id
                            } else {
                                expandedRecords + id
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }
    }

    // Modal dialog to Edit Name/Username
    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = tealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Редактировать ФИО", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Пожалуйста, введите ваше настоящее ФИО для корректного ведения электронной медицинской карты.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Полное имя (ФИО)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tealPrimary,
                            focusedLabelColor = tealPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfileName(editNameInput)
                        showEditProfile = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Сохранить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) {
                    Text("Отмена", color = Color.Gray)
                }
            }
        )
    }

    // Dialog wrapper to Book Appointment
    if (showBookDialog) {
        Dialog(onDismissRequest = { showBookDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Новая запись на приём",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = accentNavy
                        )
                        IconButton(onClick = { showBookDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close dialogue")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "1. ВЫБЕРИТЕ СПЕЦИАЛИСТА",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tealPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Selection of Doctors
                    doctors.forEach { (doc, spec) ->
                        val isSelected = selectedDoctor == doc
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) tealLight.copy(alpha = 0.6f) else Color.Transparent)
                                .border(
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) tealPrimary else Color(0xFFE0E0E0)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedDoctor = doc
                                    selectedSpecialty = spec.substringBefore(" (")
                                }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedDoctor = doc
                                        selectedSpecialty = spec.substringBefore(" (")
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = tealPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = doc,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = accentNavy
                                    )
                                    Text(
                                        text = spec,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "2. ВЫБЕРИТЕ ДАТУ ПРИЁМА",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tealPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date Row Selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        bookingDatesList.forEachIndexed { idx, dStr ->
                            val isSelected = selectedDateIdx == idx
                            val parts = dStr.split("-")
                            val day = parts.getOrNull(2) ?: dStr
                            val month = parts.getOrNull(1) ?: ""

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.95f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) tealPrimary else Color(0xFFFFFEFE))
                                    .border(
                                        border = BorderStroke(1.dp, if (isSelected) tealPrimary else Color(0xFFE0E0E0)),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedDateIdx = idx }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = day,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (isSelected) Color.White else accentNavy
                                    )
                                    Text(
                                        text = when(month) {
                                            "01" -> "Янв"
                                            "02" -> "Фев"
                                            "03" -> "Мар"
                                            "04" -> "Апр"
                                            "05" -> "Май"
                                            "06" -> "Июн"
                                            "07" -> "Июл"
                                            "08" -> "Авг"
                                            "09" -> "Сен"
                                            "10" -> "Окт"
                                            "11" -> "Ноя"
                                            "12" -> "Дек"
                                            else -> month
                                        },
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "3. ВЫБЕРИТЕ ВРЕМЯ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tealPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Simple staggered row for timeslots to represent compact flow
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val row1 = timeSlots.take(4)
                        val row2 = timeSlots.drop(4)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row1.forEach { slot ->
                                val isSelected = selectedTimeSlot == slot
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) tealPrimary else Color(0xFFF0F3F2))
                                        .clickable { selectedTimeSlot = slot }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = slot,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color.DarkGray
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(end = 40.dp)
                        ) {
                            row2.forEach { slot ->
                                val isSelected = selectedTimeSlot == slot
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) tealPrimary else Color(0xFFF0F3F2))
                                        .clickable { selectedTimeSlot = slot }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = slot,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color.DarkGray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "4. ОПИШИТЕ ЖАЛОБЫ / ПРИЧИНУ",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tealPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = bookingReasonInput,
                        onValueChange = { bookingReasonInput = it },
                        placeholder = { Text("Например: плановый осмотр, острая боль...") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("booking_reason_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tealPrimary,
                            focusedLabelColor = tealPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showBookDialog = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Отмена", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.createAppointment(
                                    doctorName = selectedDoctor,
                                    specialty = selectedSpecialty,
                                    date = bookingDatesList[selectedDateIdx],
                                    time = selectedTimeSlot,
                                    reason = bookingReasonInput.ifBlank { "Консультация врача" }
                                )
                                showBookDialog = false
                                bookingReasonInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .testTag("confirm_booking_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Записаться", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 1. GREETING HEADER COMPOSABLE
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
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF00796B), Color(0xFF00BFA5))
                    )
                )
                .padding(20.dp)
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
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$userName!",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 28.sp
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Item 1: Upcoming appointments
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Активные записи",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "$activeAppointmentsCount",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Item 2: Records
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Медицинская карта",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "$completedRecordsCount записей",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// 2. HEALTH PROFILE CABINET COMPOSABLE
@Composable
fun ProfileCabinetCard(
    user: com.example.data.db.UserEntity?,
    onEditClick: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar",
                        tint = Color(0xFF00897B),
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.fullName ?: "Пациент",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Тел: ${user?.phone ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .testTag("edit_profile_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit name",
                        tint = Color(0xFF00897B)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFECEFF1))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = if (user?.biometricEnabled == true) Color(0xFF00897B) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Вход по биометрии",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2C3E50)
                        )
                        Text(
                            text = "Авторизация по отпечатку пальца",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Switch(
                    checked = user?.biometricEnabled ?: false,
                    onCheckedChange = onBiometricToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00897B),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.testTag("biometric_switch")
                )
            }
        }
    }
}

// 3. APPOINTMENT SEGMENT SELECTOR
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentSegmentTabs(
    selectedFilter: String,
    onFilterSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE0ECEB))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            Triple("ALL", "Все записи", "appointment_tab_all"),
            Triple("ACTIVE", "Активные", "appointment_tab_active"),
            Triple("FINISHED", "Прошедшие", "appointment_tab_finished")
        ).forEach { (filter, title, tag) ->
            val isSelected = selectedFilter == filter
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onFilterSelect(filter) }
                    .padding(vertical = 8.dp)
                    .testTag(tag)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF00796B) else Color(0xFF556B69)
                )
            }
        }
    }
}

// 4. APPOINTMENT CARDS SESSION LIST
@Composable
fun AppointmentsSessionList(
    appointments: List<AppointmentEntity>,
    onCancelClick: (Int) -> Unit
) {
    if (appointments.isEmpty()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Записи не найдены",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.DarkGray
                )
                Text(
                    text = "Используйте зеленую кнопку '+' для быстрой записи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            appointments.forEach { appt ->
                AppointmentCardItem(appointment = appt, onCancelClick = onCancelClick)
            }
        }
    }
}

// APPOINTMENT CARD ITEM REDESIGN WITH CANCEL BUTTON & EXPANDED DETAIL Accent stripe
@Composable
fun AppointmentCardItem(
    appointment: AppointmentEntity,
    onCancelClick: (Int) -> Unit
) {
    val statusColor = when (appointment.status) {
        "PENDING" -> Color(0xFFFF8F00) // Beautiful Amber
        "APPROVED" -> Color(0xFF2E7D32) // Soft Forest Green
        "COMPLETED" -> Color(0xFF1565C0) // Ocean Blue
        "CANCELLED" -> Color(0xFFC62828) // Deep Coral Red
        else -> Color.Gray
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
            .border(1.dp, Color(0xFFECEFF1), RoundedCornerShape(16.dp))
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
                            color = Color(0xFF263238)
                        )
                        Text(
                            text = appointment.specialty,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

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
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${appointment.date}  в  ${appointment.time}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF37474F)
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
                            .background(Color(0xFFF1F8F6))
                            .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Ответ клиники:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = appointment.notes,
                                    fontSize = 12.sp,
                                    color = Color(0xFF33691E)
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
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(8.dp))
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

// 5. PAST MEDICAL HISTORY SECTION WITH LIVE SEARCH & COLLAPSIBILITY
@Composable
fun MedicalReportsSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    records: List<MedicalRecordEntity>,
    expandedRecords: Set<Int>,
    isFetching: Boolean,
    onFetchClick: () -> Unit,
    onRecordToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Section Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LibraryBooks,
                    contentDescription = null,
                    tint = Color(0xFF00897B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Документы и мед. отчёты",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF37474F)
                )
            }

            if (isFetching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF00897B),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(
                    onClick = onFetchClick,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("fetch_reports_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Скачать мед. отчеты",
                        tint = Color(0xFF00897B)
                    )
                }
            }
        }

        // Live Search Input bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Поиск диагнозов, врачей, рецептов...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear research", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_records_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00897B),
                unfocusedBorderColor = Color(0xFFCFD8DC),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Records list or state
        if (records.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Документы отсутствуют",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.DarkGray
                    )
                    Text(
                        text = "После приёма врачи внесут результаты в карту, результаты отобразятся тут.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                records.forEach { record ->
                    val isExpanded = expandedRecords.contains(record.id)
                    MedicalHistoryCardItem(
                        record = record,
                        isExpanded = isExpanded,
                        onExpandClick = { onRecordToggle(record.id) }
                    )
                }
            }
        }
    }
}

// EXPANDABLE MEDICAL HISTORY CARD COMPOSABLE - VERY SATISFYING & SMOOTH
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

    // Download / Export Simulation
    fun downloadReport(record: MedicalRecordEntity) {
        isDownloading = true
        coroutineScope.launch {
            delay(1200) // Realistic latency
            try {
                val fileName = "medical_report_${record.visitDate.replace("-", "")}_${record.id}.txt"
                val reportText = buildString {
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

                val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadDir, fileName)
                file.writeText(reportText)
                
                downloadedFileName = fileName
                downloadedPath = file.absolutePath
                documentContentText = reportText
                showSuccessDialog = true
            } catch (e: Exception) {
                // Fail silently or fallback
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
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Документ скачан!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF1B5E20)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Файл '$downloadedFileName' успешно сохранен на устройство в каталог Загрузки приложения.",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    
                    Text(
                        text = "Путь: AppData/Downloads/$downloadedFileName",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Превью документа:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    // Textbox visual card mimicking thermal paper monospace clinic receipt
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8F9FA),
                        border = BorderStroke(1.dp, Color(0xFFE9ECEF))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = documentContentText,
                                fontSize = 9.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF212529),
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { shareFileContent(documentContentText) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Поделиться", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) {
                    Text("Закрыть", fontSize = 12.sp)
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick)
            .border(
                border = BorderStroke(
                    width = if (isExpanded) 1.5.dp else 1.dp,
                    color = if (isExpanded) Color(0xFF00897B) else Color(0xFFE0F2F1)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("medical_record_card_${record.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Заключение осмотра",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Доктор: ${record.doctorName}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = record.visitDate,
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.Gray,
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
                    fontSize = 13.sp,
                    color = Color(0xFF37474F)
                )
                Text(
                    text = record.diagnosis,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            // Smooth expansion of prescription details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFE0ECEB))

                    // Prescription section
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = "Prescription",
                            tint = Color(0xFF00796B),
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Назначенный рецепт & Препараты",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF004D40)
                            )
                            Text(
                                text = record.prescription,
                                fontSize = 13.sp,
                                color = Color(0xFF006064)
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
                                tint = Color(0xFF558B2F),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Дополнительные рекомендации",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF33691E)
                                )
                                Text(
                                    text = record.recommendations,
                                    fontSize = 13.sp,
                                    color = Color(0xFF3E2723)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Download Report trigger button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { downloadReport(record) },
                            enabled = !isDownloading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("download_report_button_${record.id}"),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Загрузка документа...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Скачать",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Скачать медицинский отчёт (PDF/TXT)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECEFF1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Электронная подпись подтверждена врачом клиники.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// TELEGRAM BOT CONFIGURATION CARD
@Composable
fun TelegramBotCard(
    user: com.example.data.db.UserEntity?,
    onLinkClick: (String) -> Unit,
    onUnlinkClick: () -> Unit,
    onTestClick: () -> Unit
) {
    var chatIdInput by remember { mutableStateOf("") }
    val telegramBlue = Color(0xFF229ED9)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FB)), // Pleasant light ice blue
        border = BorderStroke(1.dp, Color(0xFFD4E6F1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Telegram-Бот Оповещений",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF1B4F72)
                    )
                    Text(
                        text = "@IntellectClinicBot",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = telegramBlue
                    )
                }
                if (user?.telegramChatId != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFD4EDDA),
                        border = BorderStroke(1.dp, Color(0xFFC3E6CB))
                    ) {
                        Text(
                            text = "СВЯЗАНО ✔",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF155724),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Бот автоматически информирует об изменениях статуса записей на прием и моментально доставляет назначения врачей.",
                fontSize = 11.sp,
                color = Color(0xFF2C3E50),
                lineHeight = 14.sp
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFE5EEF4))

            if (user?.telegramChatId == null) {
                // Unlinked state UI
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Для подключения бота запустите @IntellectClinicBot в Telegram и введите ваш Chat ID сюда:",
                        fontSize = 10.sp,
                        color = Color.DarkGray,
                        lineHeight = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatIdInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) chatIdInput = it },
                            placeholder = { Text("ID чата (например: 5040112)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = Color(0xFFBDC3C7),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("telegram_chat_id_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                if (chatIdInput.isNotBlank()) {
                                    onLinkClick(chatIdInput)
                                    chatIdInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("link_telegram_button")
                        ) {
                            Text("Связать", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            } else {
                // Linked state UI
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Идентификатор Чата (Chat ID):",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = user.telegramChatId,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2C3E50)
                            )
                        }

                        OutlinedButton(
                            onClick = onUnlinkClick,
                            border = BorderStroke(1.dp, Color(0xFFE74C3C).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE74C3C)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("unlink_telegram_button")
                        ) {
                            Text("Отвязать", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onTestClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34495E)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("telegram_test_alert_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Проверить доставку (Тестовое оповещение)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
