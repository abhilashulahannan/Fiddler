package com.example.fiddler.subapps.Fidland.phs3.comms

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Merges the four radio sources (Bluetooth, WiFi, NFC, Cellular) plus
 * airplane-mode state into one [CommsSnapshot] for the pill to display.
 *
 * Unlike [com.example.fiddler.subapps.Fidland.phs3.download.DownloadAggregator],
 * there's no confidence/priority merge logic here — every source is
 * independently authoritative about its own radio, so this is a straight
 * combine-and-stamp rather than a dedup/priority resolution.
 *
 * ── Airplane mode ─────────────────────────────────────────────────────────────
 * Read via [Settings.Global.AIRPLANE_MODE_ON] polling, since registering a
 * ContentObserver for a single boolean setting is more ceremony than this
 * needs — and unlike the radios above, toggling airplane mode is a rare,
 * deliberate user action, so a short poll interval costs nothing noticeable
 * while staying simple.
 */
class CommsAggregator(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val bluetoothSource = BluetoothCommsSource(context)
    private val wifiSource      = WifiCommsSource(context)
    private val nfcSource       = NfcCommsSource(context, scope)
    private val cellularSource  = CellularCommsSource(context)

    private val _airplaneModeOn = MutableStateFlow(false)
    private var airplanePollJob: Job? = null
    private val AIRPLANE_POLL_MS = 2_000L

    private val _snapshot = MutableStateFlow(CommsSnapshot())

    /** The merged comms state. Emits on every change from any source. */
    val snapshot: Flow<CommsSnapshot> = _snapshot.asStateFlow()

    fun start() {
        bluetoothSource.start()
        wifiSource.start()
        nfcSource.start()
        cellularSource.start()

        airplanePollJob = scope.launch {
            while (true) {
                _airplaneModeOn.value = readAirplaneModeSetting()
                delay(AIRPLANE_POLL_MS)
            }
        }

        combine(
            bluetoothSource.info,
            wifiSource.info,
            nfcSource.info,
            cellularSource.info,
            _airplaneModeOn,
        ) { bt, wifi, nfc, cellular, airplane ->
            CommsSnapshot(
                bluetooth = bt,
                wifi = wifi,
                nfc = nfc,
                cellular = cellular,
                airplaneModeOn = airplane,
            )
        }.onEach { merged ->
            _snapshot.value = merged
        }.launchIn(scope)
    }

    fun stop() {
        bluetoothSource.stop()
        wifiSource.stop()
        nfcSource.stop()
        cellularSource.stop()
        airplanePollJob?.cancel()
        airplanePollJob = null
        _snapshot.value = CommsSnapshot()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun readAirplaneModeSetting(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    } catch (_: Exception) {
        false
    }
}