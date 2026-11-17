// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import android.content.Context
import com.vassel.frostline.R
import com.vassel.frostline.data.AndroidClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Debug builds: simulated sensors are listed next to real ones (addresses start with "sim:"). */
@Singleton
class DebugSensorBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ble: BleSensorBackend,
    private val clock: AndroidClock,
) : SensorBackend {

    private class Sim(val id: String, val name: Int, val steps: List<SimStep>)

    private val sims = listOf(
        Sim("all", R.string.sim_all, SimScenarios.all),
        Sim("stable", R.string.sim_stable, SimScenarios.stable),
        Sim("heat", R.string.sim_heat, SimScenarios.heat),
        Sim("freeze", R.string.sim_freeze, SimScenarios.freeze),
        Sim("dropout", R.string.sim_dropout, SimScenarios.dropout),
        Sim("oscillate", R.string.sim_oscillate, SimScenarios.oscillate),
    )

    private val fakes = sims.map { ScannedSensor("$PREFIX${it.id}", context.getString(it.name), -40) }

    override fun scan(): Flow<ScanUpdate> = ble.scan()
        .onStart { emit(ScanUpdate(fakes)) }
        .map { it.copy(sensors = fakes + it.sensors) }

    override fun open(address: String, tripStartedUtcMs: Long): SensorSession {
        val sim = sims.firstOrNull { address == PREFIX + it.id } ?: return ble.open(address, tripStartedUtcMs)
        return SensorSession(FakeSensorAdapter(sim.steps, SPEED), ScaledClock(clock, SPEED, tripStartedUtcMs))
    }

    private companion object {
        const val PREFIX = "sim:"
        const val SPEED = 10.0
    }
}
