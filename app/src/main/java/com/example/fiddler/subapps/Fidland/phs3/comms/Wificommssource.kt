package com.example.fiddler.subapps.Fidland.phs3.comms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Comms source — WiFi.
 *
 * Two independent things tracked here, deliberately not conflated:
 *   • "Enabled" — the WiFi radio is powered on (WifiManager.isWifiEnabled).
 *     A WiFi adapter can be on but not joined to any network.
 *   • "Connected" — actually associated with an access point, which is when
 *     SSID / signal data becomes meaningful (ConnectivityManager.NetworkCallback
 *     on the WIFI transport).
 *
 * ── SSID availability ────────────────────────────────────────────────────────
 * WifiInfo.getSSID() / WifiManager.getConnectionInfo().ssid returns the real
 * network name ONLY when ACCESS_FINE_LOCATION is granted (already in
 * PermissionsActivity's list) AND location services are turned on at the OS
 * level. If location services are off, this silently returns "<unknown ssid>"
 * even with the permission granted — there's no API to distinguish "no
 * permission" from "location services disabled" from here, so we just strip
 * the placeholder string and report null in both cases.
 *
 * ── Signal strength ──────────────────────────────────────────────────────────
 * WifiManager.calculateSignalLevel(rssi) buckets the raw RSSI (dBm) into a
 * 0–4 bar scale matching what the OS status bar shows. Raw dBm is also kept
 * on the model for a more precise reading in State 5.
 *
 * Note: the two-arg overload calculateSignalLevel(rssi, numLevels) was
 * removed from the public API in API 30 — the framework now always buckets
 * into a fixed 5-level (0..4) scale internally, so only the one-arg form
 * compiles against modern compileSdk versions.
 */
class WifiCommsSource(
    private val context: Context,
) {
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val _info = MutableStateFlow(WifiCommsInfo(isEnabled = false))
    val info: Flow<WifiCommsInfo> = _info.asStateFlow()

    private var enabledStateReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        // ── Adapter on/off ────────────────────────────────────────────────────
        enabledStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    publishEnabled(wifiManager.isWifiEnabled)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            enabledStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )

        // ── Active connection + signal ───────────────────────────────────────
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshConnectedDetails()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Fires on signal-strength changes too — this is how we get
                // live RSSI updates without a separate poll loop.
                refreshConnectedDetails()
            }

            override fun onLost(network: Network) {
                publish(wifiManager.isWifiEnabled, ssid = null, bars = null, dbm = null)
            }
        }
        connectivityManager.registerNetworkCallback(request, networkCallback!!)

        // Prime initial state.
        publishEnabled(wifiManager.isWifiEnabled)
        refreshConnectedDetails()
    }

    fun stop() {
        enabledStateReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { /* already unregistered */ }
        }
        enabledStateReceiver = null

        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) { /* already unregistered */ }
        }
        networkCallback = null

        _info.value = WifiCommsInfo(isEnabled = false)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun publishEnabled(enabled: Boolean) {
        val current = _info.value
        _info.value = current.copy(isEnabled = enabled)
    }

    private fun refreshConnectedDetails() {
        val wifiInfo = try {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        } catch (_: Exception) {
            null
        }

        if (wifiInfo == null || wifiInfo.networkId == -1) {
            publish(wifiManager.isWifiEnabled, ssid = null, bars = null, dbm = null)
            return
        }

        val rawSsid = wifiInfo.ssid
        val ssid = rawSsid
            ?.removePrefix("\"")?.removeSuffix("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }

        val rssi = wifiInfo.rssi
        val bars = try {
            wifiManager.calculateSignalLevel(rssi) // 0..4 — see kdoc on the API-30 overload removal
        } catch (_: Exception) {
            null
        }

        publish(wifiManager.isWifiEnabled, ssid = ssid, bars = bars, dbm = rssi)
    }

    private fun publish(enabled: Boolean, ssid: String?, bars: Int?, dbm: Int?) {
        _info.value = WifiCommsInfo(
            isEnabled = enabled,
            ssid = ssid,
            signalBars = bars,
            rssiDbm = dbm,
        )
    }
}