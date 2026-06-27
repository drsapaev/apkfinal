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

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_4_5,
    )
}
