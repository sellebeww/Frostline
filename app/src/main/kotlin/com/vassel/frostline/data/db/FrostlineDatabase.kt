// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

@Database(
    entities = [
        CarrierEntity::class, SensorEntity::class, ThresholdProfileEntity::class, TripEntity::class, ReadingEntity::class,
        CustodyEventEntity::class, ExcursionEntity::class, ReportEntity::class,
    ],
    version = FrostlineDatabase.VERSION,
    exportSchema = true,
)
abstract class FrostlineDatabase : RoomDatabase() {
    abstract fun carrierDao(): CarrierDao
    abstract fun tripDao(): TripDao
    abstract fun sensorDao(): SensorDao
    abstract fun thresholdDao(): ThresholdDao
    abstract fun readingDao(): ReadingDao
    abstract fun custodyDao(): CustodyDao
    abstract fun excursionDao(): ExcursionDao
    abstract fun reportDao(): ReportDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "frostline.db"

        /**
         * [name] null gives an in-memory database. [openHelperFactory] is injectable: production passes the SQLCipher
         * factory (only [EncryptedDatabase] does), JVM tests pass null (framework SQLite, no encryption). It is
         * internal so that no other production code can open a plaintext database by accident.
         */
        internal fun create(context: Context, name: String?, openHelperFactory: SupportSQLiteOpenHelper.Factory?): FrostlineDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, FrostlineDatabase::class.java)
            } else {
                Room.databaseBuilder(context, FrostlineDatabase::class.java, name)
            }
            return builder
                .apply { if (openHelperFactory != null) openHelperFactory(openHelperFactory) }
                .addMigrations(*Migrations.ALL)
                .addCallback(DatabaseTriggers.callback)
                .build()
        }
    }
}
