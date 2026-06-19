package com.example.fiddler.subapps.Fidland.phs3.comms

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Comms source — Cellular.
 *
 * Tracks network generation (2G/3G/4G/5G), signal level (0–4 bars), and
 * carrier name. Driven by [TelephonyCallback] (API 31+) or the deprecated
 * [PhoneStateListener] (below 31) — both push updates, no polling needed.
 *
 * ── Permissions — the one gap in this module ─────────────────────────────────
 * Reading [TelephonyManager.dataNetworkType] (used to derive generation)
 * requires READ_PHONE_STATE on many OEM builds, even though AOSP docs only
 * list it as required for some of the adjacent telephony APIs. This
 * permission is currently NOT in PermissionsActivity.buildPermissionList() —
 * see the patch to that file. Without it, [TelephonyManager.getDataNetworkType]
 * throws SecurityException on some devices and silently returns
 * NETWORK_TYPE_UNKNOWN on others; either way we degrade to
 * CellularGeneration.UNKNOWN rather than crashing.
 *
 * Signal strength via [TelephonyCallback.SignalStrengthsListener] /
 * [SignalStrength.getLevel] does NOT require READ_PHONE_STATE on most
 * versions, so bars typically work even when generation reads UNKNOWN.
 *
 * Carrier name uses [TelephonyManager.networkOperatorName], which needs no
 * special permission beyond normal telephony access.
 */
class CellularCommsSource(
    private val context: Context,
) {
    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val _info = MutableStateFlow(CellularCommsInfo())
    val info: Flow<CellularCommsInfo> = _info.asStateFlow()

    private var telephonyCallback: TelephonyCallback? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    private var lastBars: Int? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DataConnectionStateListener {

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    lastBars = signalStrength.level.coerceIn(0, 4)
                    publish()
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    publish(networkTypeOverride = networkType)
                }
            }
            telephonyCallback = callback
            ContextCompat.getMainExecutor(context).let { executor ->
                try {
                    telephonyManager.registerTelephonyCallback(executor, callback)
                } catch (_: SecurityException) {
                    // READ_PHONE_STATE missing on this OEM — fall back to a
                    // best-effort static read so we at least get carrier name
                    // and "hasService", even without live updates.
                    publish()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    lastBars = signalStrength.level.coerceIn(0, 4)
                    publish()
                }

                @Suppress("DEPRECATION")
                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    publish(networkTypeOverride = networkType)
                }
            }
            phoneStateListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    listener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                )
            } catch (_: SecurityException) {
                publish()
            }
        }

        publish() // prime initial state
    }

    fun stop() {
        telephonyCallback?.let {
            try { telephonyManager.unregisterTelephonyCallback(it) } catch (_: Exception) { /* already unregistered */ }
        }
        telephonyCallback = null

        phoneStateListener?.let {
            @Suppress("DEPRECATION")
            try { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) } catch (_: Exception) { /* already unregistered */ }
        }
        phoneStateListener = null

        lastBars = null
        _info.value = CellularCommsInfo()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun publish(networkTypeOverride: Int? = null) {
        val networkType = networkTypeOverride ?: safeDataNetworkType()
        val generation = networkType?.let { networkTypeToGeneration(it) } ?: CellularGeneration.UNKNOWN

        val carrier = telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }
        val hasService = telephonyManager.networkOperatorName?.isNotBlank() == true ||
                telephonyManager.simState == TelephonyManager.SIM_STATE_READY

        _info.value = CellularCommsInfo(
            generation = generation,
            signalBars = lastBars,
            carrierName = carrier,
            hasService = hasService,
        )
    }

    /** Returns null instead of throwing if READ_PHONE_STATE isn't granted. */
    private fun safeDataNetworkType(): Int? = try {
        telephonyManager.dataNetworkType
    } catch (_: SecurityException) {
        null
    }
}