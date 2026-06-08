package com.example.data.repository

import com.example.data.db.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClinicRepository(private val database: ClinicDatabase) {
    private val userDao = database.userDao()
    private val appointmentDao = database.appointmentDao()
    private val medicalRecordDao = database.medicalRecordDao()
    private val syncLogDao = database.syncLogDao()

    // Expose flows to the ViewModel
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsersFlow()
    val allAppointments: Flow<List<AppointmentEntity>> = appointmentDao.getAllAppointmentsFlow()
    val allMedicalRecords: Flow<List<MedicalRecordEntity>> = medicalRecordDao.getAllRecordsFlow()
    val recentLogs: Flow<List<SyncLogEntity>> = syncLogDao.getRecentLogsFlow()

    // User Operations
    suspend fun getUserByPhone(phone: String): UserEntity? = userDao.getUserByPhone(phone)
    suspend fun insertUser(user: UserEntity): Long {
        val id = userDao.insertUser(user)
        addSyncLog("Registered/updated user: ${user.fullName} (${user.role})", "PATIENT_TO_STAFF")
        return id
    }
    suspend fun updateUser(user: UserEntity) {
        userDao.updateUser(user)
        addSyncLog("Updated profile for: ${user.fullName}", "PATIENT_TO_STAFF")
    }

    // Appointment Operations
    fun getAppointmentsForPatient(phone: String): Flow<List<AppointmentEntity>> =
        appointmentDao.getAppointmentsByPatientFlow(phone)

    suspend fun getAppointmentById(id: Int): AppointmentEntity? =
        appointmentDao.getAppointmentById(id)

    suspend fun insertAppointment(appointment: AppointmentEntity): AppointmentEntity {
        val id = appointmentDao.insertAppointment(appointment).toInt()
        val saved = appointment.copy(id = id)
        addSyncLog(
            logMessage = "Created appointment: ${appointment.patientName} -> ${appointment.doctorName} (${appointment.date} ${appointment.time})",
            direction = "PATIENT_TO_STAFF"
        )
        return saved
    }

    suspend fun updateAppointment(appointment: AppointmentEntity) {
        appointmentDao.updateAppointment(appointment)
        addSyncLog(
            logMessage = "Updated appointment ID #${appointment.id} state to: ${appointment.status}",
            direction = "STAFF_TO_PATIENT"
        )
    }

    suspend fun deleteAppointment(id: Int) {
        appointmentDao.deleteAppointmentById(id)
        addSyncLog("Deleted appointment ID #${id}", "SYSTEM_SYNC")
    }

    // Medical Records Operations
    fun getRecordsForPatient(phone: String): Flow<List<MedicalRecordEntity>> =
        medicalRecordDao.getRecordsByPatientFlow(phone)

    suspend fun getMedicalRecordById(id: Int): MedicalRecordEntity? =
        medicalRecordDao.getRecordById(id)

    suspend fun insertMedicalRecord(record: MedicalRecordEntity): MedicalRecordEntity {
        val id = medicalRecordDao.insertRecord(record).toInt()
        val saved = record.copy(id = id)
        addSyncLog(
            logMessage = "New medical record for patient phone ${record.patientPhone}: Diagnosis: ${record.diagnosis}",
            direction = "STAFF_TO_PATIENT"
        )
        return saved
    }

    // Logging & Simulating Sync
    suspend fun addSyncLog(logMessage: String, direction: String) {
        syncLogDao.insertLog(
            SyncLogEntity(
                logMessage = logMessage,
                direction = direction,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearLogs() = syncLogDao.clearLogs()

    // Database Seeding
    suspend fun prepopulateDatabase() {
        val staffList = userDao.getStaffUsers()
        if (staffList.isEmpty()) {
            // Seed staff (Doctors)
            val drRustam = UserEntity(
                phone = "+77071234567",
                fullName = "Dr. Rustam Sapaev",
                role = "STAFF",
                dateOfBirth = "1984-08-12",
                biometricEnabled = true
            )
            val drElena = UserEntity(
                phone = "+77019876543",
                fullName = "Dr. Elena Petrova",
                role = "STAFF",
                dateOfBirth = "1979-04-22",
                biometricEnabled = false
            )
            val drAlexander = UserEntity(
                phone = "+77025554433",
                fullName = "Dr. Alexander Smirnov",
                role = "STAFF",
                dateOfBirth = "1988-11-30",
                biometricEnabled = false
            )

            userDao.insertUser(drRustam)
            userDao.insertUser(drElena)
            userDao.insertUser(drAlexander)

            // Seed default Patient with sample phone "+77771112233" for easy testing
            val patientIvan = UserEntity(
                phone = "+77771112233",
                fullName = "Иванов Иван Иванович",
                role = "PATIENT",
                dateOfBirth = "1994-05-15",
                biometricEnabled = false
            )
            val patientJane = UserEntity(
                phone = "+77002223344",
                fullName = "Смирнова Елена Сергеевна",
                role = "PATIENT",
                dateOfBirth = "1998-09-24",
                biometricEnabled = false
            )

            val ivanId = userDao.insertUser(patientIvan)
            val janeId = userDao.insertUser(patientJane)

            // Seed some default appointments
            appointmentDao.insertAppointment(
                AppointmentEntity(
                    patientPhone = "+77771112233",
                    patientName = "Иванов Иван Иванович",
                    doctorName = "Dr. Rustam Sapaev",
                    specialty = "Стоматолог-Хирург (Dentist-Surgeon)",
                    date = getFutureDateString(1),
                    time = "10:00",
                    status = "PENDING",
                    reason = "Острая боль, консультация по имплантации"
                )
            )

            appointmentDao.insertAppointment(
                AppointmentEntity(
                    patientPhone = "+77002223344",
                    patientName = "Смирнова Елена Сергеевна",
                    doctorName = "Dr. Elena Petrova",
                    specialty = "Кардиолог (Cardiologist)",
                    date = getFutureDateString(2),
                    time = "14:30",
                    status = "APPROVED",
                    reason = "Плановый осмотр и кардиограмма"
                )
            )

            // Seed medical record
            medicalRecordDao.insertRecord(
                MedicalRecordEntity(
                    patientPhone = "+77771112233",
                    doctorName = "Dr. Rustam Sapaev",
                    diagnosis = "Кариес глубокий, пульпит 24 зуба",
                    prescription = "Ибупрофен 400мг при болях. Рекомендовано лечение каналов.",
                    visitDate = getFutureDateString(-5),
                    recommendations = "Провести рентген снимок, чистка налета"
                )
            )

            addSyncLog("Initialization of local database: Doctor profiles seeded.", "SYSTEM_SYNC")
            addSyncLog("Demo Patient profiles: Ivanov (+77771112233) & Smirnova (+77002223344) created.", "SYSTEM_SYNC")
            addSyncLog("A dental consultation with Dr. Sapaev scheduled for tomorrow.", "SYSTEM_SYNC")
        }
    }

    private fun getFutureDateString(daysAhead: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = Date(System.currentTimeMillis() + (daysAhead * 24 * 60 * 60 * 1000L))
        return dateFormat.format(date)
    }
}
