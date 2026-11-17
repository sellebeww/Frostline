// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.di

import com.vassel.frostline.data.ble.BleSensorBackend
import com.vassel.frostline.data.ble.SensorBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorBackendModule {
    @Binds abstract fun backend(impl: BleSensorBackend): SensorBackend
}
