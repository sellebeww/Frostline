// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite triggers that enforce integrity rules in the database itself. They are (re)created with IF NOT EXISTS on
 * every open, so they exist for fresh databases, migrated databases and in-memory test databases alike.
 */
object DatabaseTriggers {
    val statements: List<String> = listOf(
        """CREATE TRIGGER IF NOT EXISTS custody_events_no_update BEFORE UPDATE ON custody_events
           BEGIN SELECT RAISE(ABORT, 'custody_events is append-only'); END""",
        """CREATE TRIGGER IF NOT EXISTS custody_events_no_delete BEFORE DELETE ON custody_events
           BEGIN SELECT RAISE(ABORT, 'custody_events is append-only'); END""",
        """CREATE TRIGGER IF NOT EXISTS custody_events_trip_completed BEFORE INSERT ON custody_events
           WHEN (SELECT status FROM trips WHERE id = NEW.trip_id) = 'COMPLETED'
           BEGIN SELECT RAISE(ABORT, 'trip is completed'); END""",
        """CREATE TRIGGER IF NOT EXISTS readings_trip_completed BEFORE INSERT ON readings
           WHEN (SELECT status FROM trips WHERE id = NEW.trip_id) = 'COMPLETED'
           BEGIN SELECT RAISE(ABORT, 'trip is completed'); END""",
        """CREATE TRIGGER IF NOT EXISTS trips_single_active BEFORE INSERT ON trips
           WHEN NEW.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM trips WHERE status = 'ACTIVE')
           BEGIN SELECT RAISE(ABORT, 'another trip is active'); END""",
        """CREATE TRIGGER IF NOT EXISTS trips_completed_immutable BEFORE UPDATE ON trips
           WHEN OLD.status = 'COMPLETED'
           BEGIN SELECT RAISE(ABORT, 'trip is completed'); END""",
    )

    val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            statements.forEach { db.execSQL(it) }
        }
    }
}
