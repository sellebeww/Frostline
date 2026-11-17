// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.vassel.frostline.data.AndroidClock
import com.vassel.frostline.domain.model.MacHash
import com.vassel.frostline.domain.port.Clock
import com.vassel.frostline.domain.port.SensorAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ScannedSensor(val address: String, val name: String?, val rssi: Int) {
    val macHash: String get() = MacHash.of(address)
}

enum class ScanProblem { PERMISSION, BLUETOOTH_OFF, FAILED }

data class ScanUpdate(val sensors: List<ScannedSensor> = emptyList(), val problem: ScanProblem? = null)

/** An adapter with the clock its timestamps must come from (a simulator may run on scaled time). */
class SensorSession(val adapter: SensorAdapter, val clock: Clock)

interface SensorBackend {
    fun scan(): Flow<ScanUpdate>

    /** [tripStartedUtcMs] anchors simulated clocks so that they stay continuous across service restarts. */
    fun open(address: String, tripStartedUtcMs: Long): SensorSession
}

/** Runtime permissions per Android version (pure, so it can be unit tested). */
object BlePermissions {
    private const val SCAN = "android.permission.BLUETOOTH_SCAN"
    private const val CONNECT = "android.permission.BLUETOOTH_CONNECT"
    private const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    /** Needed to scan for sensors. Before Android 12 scanning needs location instead of the Bluetooth permissions. */
    fun forScan(sdk: Int): List<String> =
        (if (sdk >= 31) listOf(SCAN, CONNECT) else listOf(FINE_LOCATION)) + notifications(sdk)

    /** Needed to connect and to run the foreground service of type connectedDevice (Android 14 enforces it). */
    fun forMonitoring(sdk: Int): List<String> = (if (sdk >= 31) listOf(CONNECT) else emptyList()) + notifications(sdk)

    private fun notifications(sdk: Int) = if (sdk >= 33) listOf(NOTIFICATIONS) else emptyList()
}

@SuppressLint("MissingPermission") // permission state is checked explicitly before use
@Singleton
class BleSensorBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AndroidClock,
) : SensorBackend {

    override fun open(address: String, tripStartedUtcMs: Long) = SensorSession(BleEssSensorAdapter(context, address), clock)

    /** Low-power scan filtered on the ESS service UUID. The caller bounds its duration. */
    override fun scan(): Flow<ScanUpdate> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanPermission = if (Build.VERSION.SDK_INT >= 31) "android.permission.BLUETOOTH_SCAN" else "android.permission.ACCESS_FINE_LOCATION"
        val scanner = when {
            ContextCompat.checkSelfPermission(context, scanPermission) != PackageManager.PERMISSION_GRANTED -> null to ScanProblem.PERMISSION
            adapter == null || !adapter.isEnabled -> null to ScanProblem.BLUETOOTH_OFF
            else -> adapter.bluetoothLeScanner to ScanProblem.FAILED
        }
        val le = scanner.first
        if (le == null) {
            trySend(ScanUpdate(problem = scanner.second))
            awaitClose { }
            return@callbackFlow
        }
        val found = LinkedHashMap<String, ScannedSensor>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = record(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::record)
            override fun onScanFailed(errorCode: Int) {
                trySend(ScanUpdate(found.values.toList(), ScanProblem.FAILED))
            }

            private fun record(result: ScanResult) {
                val address = result.device.address
                found[address] = ScannedSensor(address, result.scanRecord?.deviceName, result.rssi)
                trySend(ScanUpdate(found.values.sortedByDescending { it.rssi }))
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(Ess.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        le.startScan(listOf(filter), settings, callback)
        awaitClose { runCatching { le.stopScan(callback) } }
    }
}
