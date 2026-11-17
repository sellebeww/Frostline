// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Add a Migration here for every [FrostlineDatabase.VERSION] bump, commit the new exported schema and extend
 * MigrationTest. Destructive migration is never enabled: an unmigratable database must fail loudly, not be wiped.
 */
object Migrations {
    /** Adds the unsigned diagnostic note to custody events. Existing rows keep NULL. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE custody_events ADD COLUMN diagnostic TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
