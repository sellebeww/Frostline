// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.vassel.frostline.domain.port.ConnectionState
import com.vassel.frostline.domain.port.SensorAdapter
import com.vassel.frostline.domain.port.SensorEvent
import com.vassel.frostline.domain.port.SensorFailure
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** A connection attempt ended (GATT status 133, remote disconnect, setup timeout). The caller reconnects with backoff. */
internal class ConnectionEnded(val status: Int) : Exception("GATT connection ended, status $status")

/**
 * Reads temperature from a BLE Environmental Sensing Service sensor. Cold flow: collecting connects and keeps
 * reconnecting (exponential backoff 1 s to 60 s with jitter); cancelling closes the GATT client.
 * Permanent problems (no ESS service, no notifiable temperature characteristic) are reported once and end the flow.
 */
@SuppressLint("MissingPermission") // permissions are checked by the UI; a missing one is reported as PERMISSION_DENIED
class BleEssSensorAdapter(
    private val context: Context,
    private val address: String,
    private val backoff: Backoff = Backoff(),
) : SensorAdapter {

    override fun events(): Flow<SensorEvent> = flow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        var attempt = 0
        while (true) {
            if (adapter == null || !adapter.isEnabled) {
                emit(SensorEvent.Connection(ConnectionState.DISCONNECTED))
                emit(SensorEvent.Failure(SensorFailure.BLUETOOTH_OFF))
                awaitBluetoothOn(adapter)
                continue
            }
            emit(SensorEvent.Connection(ConnectionState.CONNECTING))
            var permanent = false
            var permissionDenied = false
            // `catch` only sees upstream (connection) errors; an exception thrown by the collector must propagate.
            connection(adapter)
                .catch { e -> if (e is SecurityException) permissionDenied = true } // other errors are transient
                .collect { event ->
                    if (event is SensorEvent.Sample) attempt = 0
                    if (event is SensorEvent.Failure) permanent = true
                    emit(event)
                }
            if (permissionDenied) {
                emit(SensorEvent.Failure(SensorFailure.PERMISSION_DENIED))
                return@flow
            }
            emit(SensorEvent.Connection(ConnectionState.DISCONNECTED))
            if (permanent) return@flow
            delay(backoff.delayMs(attempt))
            attempt++
        }
    }

    private fun connection(adapter: BluetoothAdapter): Flow<SensorEvent> = callbackFlow {
        val session = GattSession(context, adapter.getRemoteDevice(address), channel)
        session.start()
        val setupTimeout = launch {
            delay(SETUP_TIMEOUT_MS)
            if (!session.isReady()) session.fail(TimeoutException("setup"))
        }
        awaitClose {
            setupTimeout.cancel()
            session.close()
        }
    }.buffer(Channel.UNLIMITED)

    private suspend fun awaitBluetoothOn(adapter: BluetoothAdapter?) {
        callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) trySend(Unit)
                }
            }
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            if (adapter?.isEnabled == true) trySend(Unit) // turned on between the check and the registration
            awaitClose { context.unregisterReceiver(receiver) }
        }.first()
    }

    /**
     * One GATT client. Android delivers callbacks on binder threads, so all state is guarded by [lock]. GATT allows a
     * single outstanding operation, hence the serial [ops] queue. [close] is idempotent and the only place that
     * releases the client; after it runs, late callbacks are ignored.
     */
    private class GattSession(
        private val context: Context,
        private val device: BluetoothDevice,
        private val out: SendChannel<SensorEvent>,
    ) : BluetoothGattCallback() {
        private val lock = Any()
        private var gatt: BluetoothGatt? = null
        private var closed = false
        private var ready = false
        private var busy = false
        private var humidity: Double? = null
        private val ops = ArrayDeque<() -> Boolean>()

        @Suppress("DEPRECATION") // the replacement (BluetoothGattConnectionSettings) needs a newer API than minSdk 26
        fun start() {
            synchronized(lock) {
                val g = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE)
                if (g == null) {
                    failLocked(ConnectionEnded(-1))
                } else {
                    gatt = g
                }
            }
        }

        fun isReady(): Boolean = synchronized(lock) { ready }

        fun fail(cause: Throwable) {
            synchronized(lock) { failLocked(cause) }
        }

        private fun failLocked(cause: Throwable) {
            if (!closed) out.close(cause)
        }

        /** Ends the attempt normally (no exception): used for permanent, reported failures. */
        private fun finishLocked(failure: SensorFailure) {
            if (closed) return
            out.trySend(SensorEvent.Failure(failure))
            out.close()
        }

        fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                ops.clear()
                gatt?.close()
                gatt = null
            }
        }

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            synchronized(lock) {
                if (closed) return
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> failLocked(ConnectionEnded(status)) // includes the notorious 133
                    newState == BluetoothProfile.STATE_CONNECTED -> if (!g.discoverServices()) failLocked(ConnectionEnded(-2))
                    newState == BluetoothProfile.STATE_DISCONNECTED -> failLocked(ConnectionEnded(0))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            synchronized(lock) {
                if (closed) return
                if (status != BluetoothGatt.GATT_SUCCESS) return failLocked(ConnectionEnded(status))
                val service = g.getService(Ess.SERVICE) ?: return finishLocked(SensorFailure.NO_ESS_SERVICE)
                val temp = service.getCharacteristic(Ess.TEMPERATURE)
                if (temp == null || temp.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
                    return finishLocked(SensorFailure.NO_TEMPERATURE_CHARACTERISTIC)
                }
                ops += { g.readCharacteristic(temp) }
                ops += { enableNotifications(g, temp) }
                val hum = service.getCharacteristic(Ess.HUMIDITY)
                if (hum != null && hum.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && hum.getDescriptor(Ess.CCCD) != null) {
                    ops += { g.readCharacteristic(hum) }
                    ops += { enableNotifications(g, hum) }
                }
                drainLocked()
            }
        }

        private fun enableNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
            val cccd = ch.getDescriptor(Ess.CCCD) ?: return false
            if (!g.setCharacteristicNotification(ch, true)) return false
            return writeCccd(g, cccd)
        }

        @Suppress("DEPRECATION")
        private fun writeCccd(g: BluetoothGatt, cccd: BluetoothGattDescriptor): Boolean {
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            return if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, enable) == BluetoothStatusCodes.SUCCESS
            } else {
                cccd.value = enable
                g.writeDescriptor(cccd)
            }
        }

        /** Starts the next queued operation if none is running. Must hold [lock]. */
        private fun drainLocked() {
            if (busy || closed) return
            val next = ops.removeFirstOrNull() ?: return
            busy = true
            if (!next()) {
                busy = false
                failLocked(ConnectionEnded(-3))
            }
        }

        private fun operationDone() {
            synchronized(lock) {
                busy = false
                drainLocked()
            }
        }

        private fun onValue(uuid: UUID, value: ByteArray?) {
            synchronized(lock) {
                if (closed) return
                if (uuid == Ess.TEMPERATURE) {
                    val temp = Ess.parseTemperature(value)
                    if (temp != null) out.trySend(SensorEvent.Sample(temp, humidity))
                } else if (uuid == Ess.HUMIDITY) {
                    humidity = Ess.parseHumidity(value)
                }
            }
        }

        // API 33+ callbacks carry the value
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) onValue(ch.uuid, value)
            operationDone()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            onValue(ch.uuid, value)
        }

        // Callbacks for API < 33. Newer platforms call the overrides above and these are never reached.
        @Deprecated("API 33 replaced it")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= 33) return
            if (status == BluetoothGatt.GATT_SUCCESS) onValue(ch.uuid, ch.value)
            operationDone()
        }

        @Deprecated("API 33 replaced it")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            onValue(ch.uuid, ch.value)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            synchronized(lock) {
                if (closed) return
                if (descriptor.characteristic.uuid == Ess.TEMPERATURE) {
                    if (status != BluetoothGatt.GATT_SUCCESS) return failLocked(ConnectionEnded(status))
                    ready = true
                    out.trySend(SensorEvent.Connection(ConnectionState.CONNECTED))
                }
            }
            operationDone()
        }
    }

    private companion object {
        const val SETUP_TIMEOUT_MS = 30_000L
    }
}
