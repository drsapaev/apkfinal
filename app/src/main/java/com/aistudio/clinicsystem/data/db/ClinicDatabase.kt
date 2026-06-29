package com.aistudio.clinicsystem.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.aistudio.clinicsystem.utils.TokenManager

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users ORDER BY registeredTimestamp DESC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'STAFF'")
    suspend fun getStaffUsers(): List<UserEntity>
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE patientPhone = :phone ORDER BY date ASC, time ASC")
    fun getAppointmentsByPatientFlow(phone: String): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments ORDER BY date ASC, time ASC")
    fun getAllAppointmentsFlow(): Flow<List<AppointmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: AppointmentEntity): Long

    @Update
    suspend fun updateAppointment(appointment: AppointmentEntity)

    @Query("SELECT * FROM appointments WHERE id = :id LIMIT 1")
    suspend fun getAppointmentById(id: String): AppointmentEntity?

    // M3B.4: lookup by server-assigned ID (used by WebSocket events)
    @Query("SELECT * FROM appointments WHERE serverId = :serverId LIMIT 1")
    suspend fun getAppointmentByServerId(serverId: Int): AppointmentEntity?

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointmentById(id: String)

    @Query("DELETE FROM appointments WHERE patientPhone = :phone")
    suspend fun deleteAppointmentsByPatient(phone: String)

    @Query("SELECT * FROM appointments WHERE clientRequestId = :requestId LIMIT 1")
    suspend fun getAppointmentByClientRequestId(requestId: String): AppointmentEntity?
}

@Dao
interface QueueSnapshotDao {
    @Query("SELECT * FROM queue_snapshots ORDER BY position ASC")
    fun getAllQueueSnapshotsFlow(): Flow<List<QueueSnapshotEntity>>

    @Query("SELECT * FROM queue_snapshots ORDER BY position ASC")
    suspend fun getAllQueueSnapshots(): List<QueueSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSnapshots(snapshots: List<QueueSnapshotEntity>)

    @Query("DELETE FROM queue_snapshots")
    suspend fun clearQueueSnapshots()
}

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_syncs ORDER BY timestamp ASC")
    suspend fun getAllPendingSyncs(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_syncs ORDER BY timestamp ASC")
    fun observeAllPendingSyncs(): kotlinx.coroutines.flow.Flow<List<PendingSyncEntity>>

    // M3B.3: Outbox queries
    @Query("SELECT * FROM pending_syncs WHERE status = 'PENDING' AND (nextRetryAt IS NULL OR nextRetryAt <= :now) ORDER BY timestamp ASC")
    suspend fun getPendingForRetry(now: Long = System.currentTimeMillis()): List<PendingSyncEntity>

    /**
     * Stage 3.9 (H-4 fix): returns PROCESSING rows whose `updatedAt` is
     * older than [staleBefore]. This avoids reclaiming a row that is
     * currently being processed by another SyncWorker invocation.
     *
     * 5-minute threshold: long enough for any legitimate network call to
     * complete; short enough that a crashed worker's row is recovered
     * on the next sync cycle.
     */
    @Query("SELECT * FROM pending_syncs WHERE status = 'PROCESSING' AND updatedAt < :staleBefore ORDER BY timestamp ASC")
    suspend fun getStuckProcessing(staleBefore: Long): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_syncs WHERE status = 'DEAD_LETTER' ORDER BY timestamp ASC")
    suspend fun getDeadLettered(): List<PendingSyncEntity>

    @Query("UPDATE pending_syncs SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """UPDATE pending_syncs
           SET status = :status, retryCount = :retryCount,
               lastError = :error, nextRetryAt = :nextRetryAt,
               updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun updateRetryState(
        id: String,
        status: String,
        retryCount: Int,
        error: String?,
        nextRetryAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Stage 3.6 (NET-7 fix): updates retry state with the HTTP status code
     * of the last failure. Used by [ClinicRepository.retryUnsyncedWrites]
     * to distinguish 4xx (DEAD_LETTER) from 5xx (retry).
     */
    @Query(
        """UPDATE pending_syncs
           SET status = :status, retryCount = :retryCount,
               lastError = :error, nextRetryAt = :nextRetryAt,
               lastHttpCode = :httpCode,
               updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun updateRetryStateWithHttpCode(
        id: String,
        status: String,
        retryCount: Int,
        error: String?,
        nextRetryAt: Long?,
        httpCode: Int?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM pending_syncs WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSync(sync: PendingSyncEntity)

    @Delete
    suspend fun deletePendingSync(sync: PendingSyncEntity)

    @Query("DELETE FROM pending_syncs")
    suspend fun clearPendingSyncs()

    /**
     * Stage 3.2 (H-1 fix): atomically claims PENDING + stale-PROCESSING
     * rows for processing. The SELECT + UPDATE happens in a single Room
     * transaction, so concurrent SyncWorker invocations CANNOT claim the
     * same row.
     *
     * Returns the list of claimed rows (with status already flipped to
     * PROCESSING). The caller processes each row, then either marks it
     * COMPLETED + deletes, or calls [updateRetryStateWithHttpCode] on
     * failure.
     *
     * The [staleBefore] parameter is the cutoff for stale-PROCESSING
     * recovery (typically `now - 5min`).
     */
    @Transaction
    suspend fun claimForProcessing(staleBefore: Long): List<PendingSyncEntity> {
        val pending = getPendingForRetry()
        val stuck = getStuckProcessing(staleBefore)
        val allToProcess = pending + stuck
        // Mark each row as PROCESSING — sets `updatedAt` to now, which
        // prevents another worker from reclaiming it within the 5-min
        // stale threshold.
        for (sync in allToProcess) {
            updateStatus(sync.id, "PROCESSING")
        }
        return allToProcess
    }
}

@Dao
interface MedicalRecordDao {
    @Query("SELECT * FROM medical_records WHERE patientPhone = :phone ORDER BY timestamp DESC")
    fun getRecordsByPatientFlow(phone: String): Flow<List<MedicalRecordEntity>>

    @Query("SELECT * FROM medical_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<MedicalRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MedicalRecordEntity): Long

    @Query("SELECT * FROM medical_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): MedicalRecordEntity?

    // Stage 3.5 (H-9 fix): lookup by server-assigned ID — used by NBR
    // saveFetchResult to dedup server-fetched records.
    @Query("SELECT * FROM medical_records WHERE serverId = :serverId LIMIT 1")
    suspend fun getMedicalRecordByServerId(serverId: Int): MedicalRecordEntity?

    @Query("DELETE FROM medical_records WHERE patientPhone = :phone")
    suspend fun deleteRecordsByPatient(phone: String)
}

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 30")
    fun getRecentLogsFlow(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearLogs()
}

/**
 * P-04: DoctorDao — DAO для справочника врачей.
 */
@Dao
interface DoctorDao {
    @Query("SELECT * FROM doctors WHERE isActive = 1 ORDER BY fullName")
    fun getAllDoctors(): Flow<List<DoctorEntity>>

    @Query("SELECT * FROM doctors WHERE id = :id")
    suspend fun getDoctorById(id: String): DoctorEntity?

    @Query("SELECT * FROM doctors WHERE serverId = :serverId")
    suspend fun getDoctorByServerId(serverId: Int): DoctorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctors(doctors: List<DoctorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctor(doctor: DoctorEntity)

    @Query("DELETE FROM doctors")
    suspend fun clearDoctors()

    @Query("SELECT MAX(updatedAt) FROM doctors")
    suspend fun getLastUpdated(): Long?

    @Query("SELECT COUNT(*) FROM doctors")
    suspend fun getDoctorCount(): Int
}

@Database(
    entities = [
        UserEntity::class,
        AppointmentEntity::class,
        MedicalRecordEntity::class,
        SyncLogEntity::class,
        QueueSnapshotEntity::class,
        PendingSyncEntity::class,
        DoctorEntity::class
    ],
    // P-04: bumped 7 → 8. Migration 7→8 adds doctors table for backend-synced
    // doctor directory (replaces hardcoded 3-doctor list in PatientScreen.kt).
    version = 8,
    // M1/E4.1: exportSchema is now true. Room will emit a JSON schema file
    // to app/schemas/com.aistudio.clinicsystem.data.db.ClinicDatabase/8.json
    // on every build. This file must be committed to git — it is the
    // baseline used by MigrationTestHelper in E4.4 to write migration tests.
    exportSchema = true
)
abstract class ClinicDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun medicalRecordDao(): MedicalRecordDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun queueSnapshotDao(): QueueSnapshotDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun doctorDao(): DoctorDao  // P-04: doctor directory DAO

    companion object {
        @Volatile
        private var INSTANCE: ClinicDatabase? = null

        fun getDatabase(context: Context): ClinicDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Failed to load sqlcipher library")
                }

                // E1.6: if encrypted storage is unavailable, getOrCreateDatabaseKey
                // returns null. We MUST refuse to open the database in that case —
                // opening with an empty passphrase would silently store PHI unencrypted.
                val passphrase = TokenManager.getOrCreateDatabaseKey(context)
                    ?: throw IllegalStateException(
                        "EncryptedSharedPreferences unavailable — refusing to open " +
                            "SQLCipher database with null passphrase. " +
                            "User must be prompted to re-authenticate."
                    )
                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClinicDatabase::class.java,
                    "clinic_database"
                )
                .openHelperFactory(factory)
                // Stage 4.2 (PERF-9 fix): enable WAL (Write-Ahead Logging).
                // Without WAL, SQLite uses rollback journaling — readers block
                // writers and vice versa. With WAL, reads and writes can proceed
                // concurrently on different connections, which prevents UI
                // jank when SyncWorker writes in the background while the UI
                // is reading appointments.
                //
                // SQLCipher 4.5.4 supports WAL; verify with
                // `adb shell sqlite3 /data/data/.../databases/clinic_database
                //   "PRAGMA journal_mode;"` after first open — should print "wal".
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // M1/E4.2: fallbackToDestructiveMigration() was removed.
                // Future schema changes MUST be accompanied by an explicit
                // Migration object (see Migrations.kt). If a migration is
                // missing, Room will throw IllegalStateException on upgrade
                // rather than silently wiping user data.
                //
                // Stage 3.1: also removed fallbackToDestructiveMigrationOnDowngrade
                // — for a medical app, even downgrade data loss is unacceptable.
                // On downgrade, Room will throw IllegalStateException; the user
                // must re-install the correct version.
                // M1/E4.3: register known migrations.
                .addMigrations(*Migrations.ALL)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
