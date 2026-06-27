package com.aistudio.clinicsystem.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * M3B.3: Room migrations for [ClinicDatabase].
 *
 * MIGRATION_4_5: Outbox pattern — changes pending_syncs table:
 *   - Primary key: id Int (autoGenerate) → id TEXT (UUID)
 *   - New columns: status, lastError, nextRetryAt, updatedAt
 *   - Index on (status, nextRetryAt) for efficient retry queries
 */
object Migrations {

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // M3B.3: Recreate pending_syncs with UUID primary key + outbox columns.
            // SQLite doesn't support ALTER TABLE to change primary key, so we
            // create a new table, copy data, and swap.

            // 1. Create new table with UUID primary key
            db.execSQL("""
                CREATE TABLE pending_syncs_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    clientRequestId TEXT NOT NULL,
                    clinicId TEXT NOT NULL DEFAULT 'clinic_base',
                    timestamp INTEGER NOT NULL DEFAULT 0,
                    retryCount INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    lastError TEXT,
                    nextRetryAt INTEGER,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // 2. Copy existing data — convert Int id to String, set new columns
            db.execSQL("""
                INSERT INTO pending_syncs_new (id, type, payload, clientRequestId, clinicId, timestamp, retryCount, status, lastError, nextRetryAt, updatedAt)
                SELECT CAST(id AS TEXT), type, payload, clientRequestId, clinicId, timestamp, retryCount, 'PENDING', NULL, NULL, timestamp
                FROM pending_syncs
            """.trimIndent())

            // 3. Drop old table
            db.execSQL("DROP TABLE pending_syncs")

            // 4. Rename new table
            db.execSQL("ALTER TABLE pending_syncs_new RENAME TO pending_syncs")

            // 5. Create index for efficient retry queries
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_syncs_status ON pending_syncs(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_syncs_next_retry ON pending_syncs(status, nextRetryAt)")
        }
    }

    /**
     * M3B.4: Migration 5 → 6 — UUID for appointments and medical_records.
     *
     * Changes:
     *   - appointments.id: Int (autoGenerate) → String (UUID)
     *   - appointments.serverId: new Int? column (backend-assigned ID)
     *   - medical_records.id: Int (autoGenerate) → String (UUID)
     *   - medical_records.serverId: new Int? column
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Appointments: recreate with UUID PK + serverId
            db.execSQL("""
                CREATE TABLE appointments_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    serverId INTEGER,
                    patientPhone TEXT NOT NULL,
                    patientName TEXT NOT NULL,
                    doctorName TEXT NOT NULL,
                    specialty TEXT NOT NULL,
                    date TEXT NOT NULL,
                    time TEXT NOT NULL,
                    status TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    clinicId TEXT NOT NULL DEFAULT 'clinic_base',
                    notes TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    clientRequestId TEXT,
                    version INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())

            // Copy existing data — convert Int id to String, set serverId = old id
            db.execSQL("""
                INSERT INTO appointments_new (id, serverId, patientPhone, patientName, doctorName, specialty, date, time, status, reason, clinicId, notes, updatedAt, clientRequestId, version)
                SELECT CAST(id AS TEXT), id, patientPhone, patientName, doctorName, specialty, date, time, status, reason, clinicId, notes, updatedAt, clientRequestId, version
                FROM appointments
            """.trimIndent())
            db.execSQL("DROP TABLE appointments")
            db.execSQL("ALTER TABLE appointments_new RENAME TO appointments")

            // Medical records: recreate with UUID PK + serverId
            db.execSQL("""
                CREATE TABLE medical_records_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    serverId INTEGER,
                    patientPhone TEXT NOT NULL,
                    doctorName TEXT NOT NULL,
                    diagnosis TEXT NOT NULL,
                    prescription TEXT NOT NULL,
                    visitDate TEXT NOT NULL,
                    clinicId TEXT NOT NULL DEFAULT 'clinic_base',
                    recommendations TEXT NOT NULL DEFAULT '',
                    timestamp INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO medical_records_new (id, serverId, patientPhone, doctorName, diagnosis, prescription, visitDate, clinicId, recommendations, timestamp)
                SELECT CAST(id AS TEXT), id, patientPhone, doctorName, diagnosis, prescription, visitDate, clinicId, recommendations, timestamp
                FROM medical_records
            """.trimIndent())
            db.execSQL("DROP TABLE medical_records")
            db.execSQL("ALTER TABLE medical_records_new RENAME TO medical_records")
        }
    }

    /**
     * Stage 3.1 (H-8 fix): Migration 6 → 7 — adds database indices for
     * referential integrity and query performance, plus the new columns
     * introduced in Stage 3:
     *   - appointments.etag (TEXT, nullable)
     *   - medical_records.updatedAt, version, etag (TEXT/INTEGER)
     *   - pending_syncs.lastHttpCode (INTEGER, nullable)
     *   - users unique index on phone
     *
     * No data is lost — all new columns have safe defaults.
     *
     * Foreign keys are NOT added in this migration (would require
     * backfilling orphan rows); they are scheduled for Stage 8 (multi-clinic).
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── appointments: add etag column + indices ──────────────
            db.execSQL("ALTER TABLE appointments ADD COLUMN etag TEXT")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_patientPhone ON appointments(patientPhone)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_serverId ON appointments(serverId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_clientRequestId ON appointments(clientRequestId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_status ON appointments(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_clinicId ON appointments(clinicId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_date_time ON appointments(date, time)")

            // ── medical_records: add updatedAt, version, etag + indices ─
            db.execSQL("ALTER TABLE medical_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE medical_records ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE medical_records ADD COLUMN etag TEXT")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_records_patientPhone ON medical_records(patientPhone)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_records_serverId ON medical_records(serverId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_records_clinicId ON medical_records(clinicId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_records_visitDate ON medical_records(visitDate)")

            // ── pending_syncs: add lastHttpCode + index on clientRequestId ──
            db.execSQL("ALTER TABLE pending_syncs ADD COLUMN lastHttpCode INTEGER")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_syncs_clientRequestId ON pending_syncs(clientRequestId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_syncs_clinicId ON pending_syncs(clinicId)")

            // ── users: unique index on phone, plus role/clinicId indices ──
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_phone ON users(phone)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_users_role ON users(role)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_users_clinicId ON users(clinicId)")

            // ── queue_snapshots: indices ──
            db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_snapshots_appointmentId ON queue_snapshots(appointmentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_snapshots_clinicId ON queue_snapshots(clinicId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_snapshots_status ON queue_snapshots(status)")

            // ── sync_logs: index on timestamp (for DESC LIMIT queries) ──
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_logs_timestamp ON sync_logs(timestamp)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
    )
}
