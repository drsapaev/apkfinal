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
    suspend fun getAppointmentById(id: Int): AppointmentEntity?

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteAppointmentById(id: Int)

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
    @Query("SELECT * FROM pending_syncs ORDER BY id ASC")
    suspend fun getAllPendingSyncs(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_syncs ORDER BY id ASC")
    fun observeAllPendingSyncs(): kotlinx.coroutines.flow.Flow<List<PendingSyncEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSync(sync: PendingSyncEntity)

    @Delete
    suspend fun deletePendingSync(sync: PendingSyncEntity)

    @Query("DELETE FROM pending_syncs")
    suspend fun clearPendingSyncs()
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
    suspend fun getRecordById(id: Int): MedicalRecordEntity?

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

@Database(
    entities = [
        UserEntity::class,
        AppointmentEntity::class,
        MedicalRecordEntity::class,
        SyncLogEntity::class,
        QueueSnapshotEntity::class,
        PendingSyncEntity::class
    ],
    version = 4,
    // M1/E4.1: exportSchema is now true. Room will emit a JSON schema file
    // to app/schemas/com.aistudio.clinicsystem.data.db.ClinicDatabase/4.json
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

    companion object {
        @Volatile
        private var INSTANCE: ClinicDatabase? = null

        fun getDatabase(context: Context): ClinicDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (e: Exception) {
                    android.util.Log.e("ClinicDatabase", "Failed to load sqlcipher library", e)
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
                // M1/E4.2: fallbackToDestructiveMigration() was removed.
                // Future schema changes MUST be accompanied by an explicit
                // Migration object (see Migrations.kt). If a migration is
                // missing, Room will throw IllegalStateException on upgrade
                // rather than silently wiping user data.
                //
                // We DO allow destructive fallback on DOWNGRADE — that's
                // safe (e.g. user installed a beta build then rolled back).
                .fallbackToDestructiveMigrationOnDowngrade()
                // M1/E4.3: register known migrations (currently just 4→5 template).
                .addMigrations(*Migrations.ALL)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
