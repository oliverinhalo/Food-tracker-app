package dev.foodtracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history. Migrations rather than destructive fallback: a user's food diary is the whole
 * point of the app, and wiping it on an update would be unforgivable.
 */
object Migrations {

    /** v2 adds the offline re-analysis queue. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_analyses` (
                    `captureId` TEXT NOT NULL,
                    `imagePath` TEXT NOT NULL,
                    `queuedAtMillis` INTEGER NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `lastAttemptMillis` INTEGER,
                    PRIMARY KEY(`captureId`)
                )
                """.trimIndent(),
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
