package com.example.fiddler.subapps.Fidland.phs3.comms

/**
 * Phs3 module — Comms — shared state models.
 *
 * Mirrors the Download module's pattern: one small data class per signal
 * source, merged by [CommsAggregator] into a single [CommsSnapshot] that the
 * handler renders.
 *
 * Unlike Download (which tracks one thing — "is something downloading" —
 * with rich progress data), Comms tracks FOUR independent radios that can
 * all be on simultaneously. There's no "primary" entry to pick; the handler
 * shows whichever subset is currently active, side by side.
 */

/** Cellular generation, mirroring DownloadNetworkType's naming so the icon
 *  vocabulary stays consistent across phs3 modules. */
enum class CellularGeneration {
    G2, G3, G4, G5, UNKNOWN
}

/**
 * Bluetooth radio state.
 *
 * @param isEnabled       Whether the BT adapter itself is powered on.
 * @param connectedDevice Name of the actively connected device (e.g. "Pixel Buds Pro"),
 *                         or null if the radio is on but nothing is connected.
 */
data class BluetoothCommsInfo(
    val isEnabled: Boolean,
    val connectedDevice: String? = null,
)

/**
 * WiFi radio state.
 *
 * @param isEnabled  Whether the WiFi adapter itself is powered on.
 * @param ssid       Connected network name, or null if on but not joined to
 *                    a network (or SSID unavailable — see source kdoc).
 * @param signalBars Signal level in [0, 4], or null if not connected.
 * @param rssiDbm    Raw signal strength in dBm, for State 5 detail display.
 */
data class WifiCommsInfo(
    val isEnabled: Boolean,
    val ssid: String? = null,
    val signalBars: Int? = null,
    val rssiDbm: Int? = null,
)

/**
 * NFC radio state. On/off is the full picture — NFC has no "connected device"
 * concept outside of an active tap-to-pair/tap-to-pay session, which is too
 * transient to track meaningfully here.
 */
data class NfcCommsInfo(
    val isEnabled: Boolean,
)

/**
 * Cellular radio state.
 *
 * @param generation  2G/3G/4G/5G, or UNKNOWN if undetermined (e.g. missing
 *                     READ_PHONE_STATE on this OEM — see CellularCommsSource kdoc).
 * @param signalBars  Signal level in [0, 4], or null if no service.
 * @param carrierName Network operator display name, e.g. "Airtel". Null if unavailable.
 * @param hasService  False when there's no SIM / no signal / airplane mode —
 *                     distinct from "radio enabled but generation unknown."
 */
data class CellularCommsInfo(
    val generation: CellularGeneration = CellularGeneration.UNKNOWN,
    val signalBars: Int? = null,
    val carrierName: String? = null,
    val hasService: Boolean = false,
)

/**
 * Aggregated snapshot of all four radios at a point in time. This is what
 * [CommsAggregator] emits and what [CommsPhs3Handler] renders.
 *
 * @param airplaneModeOn When true, the handler shows a single airplane-mode
 *                         indicator and suppresses the per-radio icons below
 *                         it — airplane mode is the "all comms off" master
 *                         switch and showing four crossed-out icons under it
 *                         is just noise.
 */
data class CommsSnapshot(
    val bluetooth: BluetoothCommsInfo = BluetoothCommsInfo(isEnabled = false),
    val wifi: WifiCommsInfo = WifiCommsInfo(isEnabled = false),
    val nfc: NfcCommsInfo = NfcCommsInfo(isEnabled = false),
    val cellular: CellularCommsInfo = CellularCommsInfo(),
    val airplaneModeOn: Boolean = false,
) {
    /**
     * Whether anything in this snapshot is worth showing in the pill at all.
     * The phs3 slot should deactivate entirely when this is false (mirrors
     * DownloadInfo.isActive() / the aggregator's "emptyMap = idle" idiom).
     */
    fun hasAnythingToShow(): Boolean =
        airplaneModeOn || bluetooth.isEnabled || wifi.isEnabled || nfc.isEnabled || cellular.hasService
}

// ── Formatting helpers ────────────────────────────────────────────────────────

/** Maps a raw dataNetworkType / TelephonyManager network type constant to our enum. */
fun networkTypeToGeneration(networkType: Int): CellularGeneration {
    // Constants from android.telephony.TelephonyManager — duplicated as Ints
    // here (rather than importing TelephonyManager into this pure-state file)
    // so this file stays android-framework-agnostic like Downloadstate.kt.
    return when (networkType) {
        1, 2, 4, 7, 11, 16 -> CellularGeneration.G2   // GPRS, EDGE, CDMA, 1xRTT, IDEN, GSM
        3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> CellularGeneration.G3 // UMTS, EVDO_0/A/B, HSDPA, HSUPA, HSPA, EHRPD, HSPAP, TD_SCDMA
        13, 19 -> CellularGeneration.G4                 // LTE, LTE_CA
        20 -> CellularGeneration.G5                      // NR
        else -> CellularGeneration.UNKNOWN
    }
}

/** Short display label for a [CellularGeneration], e.g. for the icon text. */
fun CellularGeneration.label(): String = when (this) {
    CellularGeneration.G2 -> "2G"
    CellularGeneration.G3 -> "3G"
    CellularGeneration.G4 -> "4G"
    CellularGeneration.G5 -> "5G"
    CellularGeneration.UNKNOWN -> "—"
}