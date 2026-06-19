package com.example.fiddler.subapps.Fidland.phs3.comms

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Comms source — Bluetooth.
 *
 * Tracks two things via broadcasts (no polling needed):
 *   1. Adapter on/off — [BluetoothAdapter.ACTION_STATE_CHANGED].
 *   2. Currently connected device name — [BluetoothDevice.ACTION_ACL_CONNECTED]
 *      / [BluetoothDevice.ACTION_ACL_DISCONNECTED]. ACL (link-layer) connection
 *      is the right granularity here: it fires for any connected BT device
 *      (headset, earbuds, watch, car kit) regardless of which profile it's
 *      using, so we don't need a separate listener per profile (A2DP/HFP/etc).
 *
 * If multiple devices are connected simultaneously (rare but possible — e.g.
 * a watch + earbuds), we show the most recently connected one. The full set
 * is kept internally in case ControlsPanel wants to list all of them later.
 *
 * ── Permissions ───────────────────────────────────────────────────────────────
 * Reading [BluetoothDevice.getName] requires BLUETOOTH_CONNECT on API 31+
 * (already in PermissionsActivity's list). Without it the name read throws
 * SecurityException, which we catch and fall back to "Bluetooth device" so
 * the indicator still shows *something* is connected rather than crashing.
 */
class BluetoothCommsSource(
    private val context: Context,
) {
    private val adapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
        manager?.adapter
    }

    private val _info = MutableStateFlow(BluetoothCommsInfo(isEnabled = false))
    val info: Flow<BluetoothCommsInfo> = _info.asStateFlow()

    /** Connected device names, most-recent-last. Cleared entry removes by address. */
    private val connectedDevices = linkedMapOf<String, String>() // address -> name

    private var receiver: BroadcastReceiver? = null

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> handleAdapterStateChanged()
                    BluetoothDevice.ACTION_ACL_CONNECTED -> handleDeviceConnected(intent)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDeviceDisconnected(intent)
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_EXPORTED
        )

        // Prime initial state — the receiver only tells us about *changes*
        // from here on, so we need one synchronous read of current state.
        handleAdapterStateChanged()
    }

    fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { /* already unregistered */ }
        }
        receiver = null
        connectedDevices.clear()
        _info.value = BluetoothCommsInfo(isEnabled = false)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun handleAdapterStateChanged() {
        val enabled = adapter?.isEnabled == true
        if (!enabled) connectedDevices.clear()
        publish(enabled)
    }

    private fun handleDeviceConnected(intent: Intent) {
        val device = getDeviceExtra(intent) ?: return
        val name = safeDeviceName(device) ?: "Bluetooth device"
        connectedDevices[device.address] = name
        publish(adapter?.isEnabled == true)
    }

    private fun handleDeviceDisconnected(intent: Intent) {
        val device = getDeviceExtra(intent) ?: return
        connectedDevices.remove(device.address)
        publish(adapter?.isEnabled == true)
    }

    private fun getDeviceExtra(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    /** Returns null (instead of throwing) if BLUETOOTH_CONNECT isn't granted. */
    private fun safeDeviceName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    }

    private fun publish(enabled: Boolean) {
        _info.value = BluetoothCommsInfo(
            isEnabled = enabled,
            connectedDevice = connectedDevices.values.lastOrNull(),
        )
    }
}