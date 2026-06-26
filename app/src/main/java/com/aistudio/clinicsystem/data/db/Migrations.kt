package com.aistudio.clinicsystem.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * M1/E4.3: Room migrations for [ClinicDatabase].
 *
 * History: prior to M0, the database used `fallbackToDestructiveMigration()`
 * which silently wiped user data on every schema change. The DB version was
 * bumped to 4 during development without writing any migration — so any
 * existing user with version 1, 2, or 3 already had their data wiped at
 * some point. There is no way to recover those migrations.
 *
 * Starting from M1/E4.2, destructive migration is REMOVED. Every future
 * schema bump MUST be accompanied by a [Migration] object registered in
 * [ALL]. The [MIGRATION_4_5] below is a no-op template that demonstrates
 * the pattern — replace its body with real SQL when version 5 introduces
 * actual schema changes.
 *
 * Migration testing is set up in M1/E4.4 (MigrationTest.kt) using
 * MigrationTestHelper from androidx.room:room-testing.
 *
 * Reference: https://developer.android.com/training/data-storage/room/migrating-db-versions
 */
object Migrations {

    /**
     * Example migration: 4 → 5.
     *
     * Currently a no-op because no schema changes have been made between
     * version 4 and the next planned version 5. When version 5 introduces
     * real changes (e.g. adding a column, creating an index, renaming a
     * table), update the @Database version to 5 and put the SQL here.
     *
     * Example body for adding a column:
     * ```
     * override fun migrate(db: SupportSQLiteDatabase) {
     *     db.execSQL("ALTER TABLE appointments ADD COLUMN sync_priority INTEGER NOT NULL DEFAULT 0")
     *     db.execSQL("CREATE INDEX IF NOT EXISTS index_appointments_date ON appointments(date)")
     * }
     * ```
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No-op: version 5 has not yet introduced schema changes.
            // When you bump @Database(version = 5), put your ALTER TABLE /
            // CREATE INDEX / DROP+CREATE statements here.
            //
            // IMPORTANT: never use empty body for a real migration — if the
            // schema actually changed, Room will detect the mismatch between
            // the migrated schema and the declared @Entity structure and
            // throw IllegalStateException at runtime.
        }
    }

    /** All registered migrations, indexed by (from, to) version pairs. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_4_5,
        // Add MIGRATION_5_6, MIGRATION_6_7, ... here as the schema evolves.
    )
}
