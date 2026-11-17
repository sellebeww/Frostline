// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.di

import android.content.Context
import com.vassel.frostline.data.AndroidClock
import com.vassel.frostline.data.db.DatabaseState
import com.vassel.frostline.data.db.DatabaseUnavailableException
import com.vassel.frostline.data.db.EncryptedDatabase
import com.vassel.frostline.data.db.FrostlineDatabase
import com.vassel.frostline.data.OperatorProfileStore
import com.vassel.frostline.data.OperatorStore
import com.vassel.frostline.data.AppSettings
import com.vassel.frostline.data.PrefsAppSettings
import com.vassel.frostline.data.db.RoomCarrierRepository
import com.vassel.frostline.data.db.RoomThresholdRepository
import com.vassel.frostline.data.db.RoomCustodyRepository
import com.vassel.frostline.data.db.RoomExcursionRepository
import com.vassel.frostline.data.db.RoomReadingRepository
import com.vassel.frostline.data.db.RoomReportRepository
import com.vassel.frostline.data.db.RoomSensorRepository
import com.vassel.frostline.data.db.RoomTransactionRunner
import com.vassel.frostline.data.db.RoomTripRepository
import com.vassel.frostline.data.location.FusedLocationProvider
import com.vassel.frostline.data.report.PdfReportRenderer
import com.vassel.frostline.data.report.ReportPdfWriter
import com.vassel.frostline.data.security.DatabaseKeyManager
import com.vassel.frostline.data.security.KeystoreSecretWrapper
import com.vassel.frostline.data.security.KeystoreSigner
import com.vassel.frostline.domain.port.CarrierRepository
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.ThresholdRepository
import com.vassel.frostline.service.AndroidMonitorController
import com.vassel.frostline.service.MonitorController
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.PublicKeySource
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.ReportRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.domain.port.Signer
import com.vassel.frostline.domain.port.TransactionRunner
import com.vassel.frostline.domain.port.TripRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val KEY_FILE = "frostline_db_key.bin"
    private const val WRAP_ALIAS = "frostline_db_wrap_v1"

    /** The key file lives in noBackupFilesDir, in addition to the backup exclusions in the manifest rules. */
    @Provides
    @Singleton
    fun databaseKeyManager(@ApplicationContext context: Context) =
        DatabaseKeyManager(File(context.noBackupFilesDir, KEY_FILE), KeystoreSecretWrapper(WRAP_ALIAS))

    /** Opened once, on first use. First injection must happen off the main thread (it does disk I/O). */
    @Provides
    @Singleton
    fun databaseState(@ApplicationContext context: Context, keyManager: DatabaseKeyManager): DatabaseState =
        EncryptedDatabase.open(context, keyManager)

    /** Throws [DatabaseUnavailableException] with a typed reason instead of creating or opening anything else. */
    @Provides
    @Singleton
    fun database(state: DatabaseState): FrostlineDatabase = when (state) {
        is DatabaseState.Ready -> state.database
        is DatabaseState.Failed -> throw DatabaseUnavailableException(state.failure)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun trips(impl: RoomTripRepository): TripRepository
    @Binds abstract fun readings(impl: RoomReadingRepository): ReadingRepository
    @Binds abstract fun custody(impl: RoomCustodyRepository): CustodyRepository
    @Binds abstract fun excursions(impl: RoomExcursionRepository): ExcursionRepository
    @Binds abstract fun sensors(impl: RoomSensorRepository): SensorRepository
    @Binds abstract fun transactions(impl: RoomTransactionRunner): TransactionRunner
    @Binds abstract fun signer(impl: KeystoreSigner): Signer
    @Binds abstract fun publicKey(impl: KeystoreSigner): PublicKeySource
    @Binds abstract fun pdfWriter(impl: PdfReportRenderer): ReportPdfWriter
    @Binds abstract fun reports(impl: RoomReportRepository): ReportRepository
    @Binds abstract fun clock(impl: AndroidClock): Clock
    @Binds abstract fun carriers(impl: RoomCarrierRepository): CarrierRepository
    @Binds abstract fun thresholds(impl: RoomThresholdRepository): ThresholdRepository
    @Binds abstract fun operators(impl: OperatorProfileStore): OperatorStore
    @Binds abstract fun settings(impl: PrefsAppSettings): AppSettings
    @Binds abstract fun monitor(impl: AndroidMonitorController): MonitorController
    @Binds abstract fun location(impl: FusedLocationProvider): LocationProvider
}
