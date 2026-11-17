// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.di

import com.vassel.frostline.data.db.DatabaseState
import com.vassel.frostline.data.security.KeystoreSigner
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.ExcursionRepository
import com.vassel.frostline.data.ble.SensorBackend
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.SensorRepository
import com.vassel.frostline.notification.AlarmNotifier
import com.vassel.frostline.service.MonitorStatusStore
import com.vassel.frostline.domain.port.TransactionRunner
import com.vassel.frostline.domain.port.TripRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Access to the persistence graph for classes Hilt does not inject; also makes Hilt validate the whole graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataEntryPoint {
    fun databaseState(): DatabaseState
    fun trips(): TripRepository
    fun readings(): ReadingRepository
    fun custody(): CustodyRepository
    fun excursions(): ExcursionRepository
    fun transactions(): TransactionRunner
    fun signer(): KeystoreSigner
    fun sensors(): SensorRepository
    fun sensorBackend(): SensorBackend
    fun alarmNotifier(): AlarmNotifier
    fun statusStore(): MonitorStatusStore
}
