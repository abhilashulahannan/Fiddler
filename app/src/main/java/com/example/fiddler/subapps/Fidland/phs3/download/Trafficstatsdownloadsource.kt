package com.example.fiddler.subapps.Fidland.phs3.download

import android.net.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Source 3 — TrafficStats (global RX bytes).
 *
 * Covers: EVERYTHING that moves bytes — streaming, background sync, custom
 * HTTP clients, anything Chrome or any app downloads even without a
 * notification. This is the catch-all.
 *
 * Limitation: no title, no per-file progress, no way to know WHAT is
 * downloading — only that bytes are flowing at a certain speed.
 *
 * Role in the aggregator:
 *   • If another source (Notification / DownloadManager) is already active,
 *     this source enriches it with a live speed reading.
 *   • If NO other source is active but traffic is high (> [SPEED_THRESHOLD_BPS]),
 *     this source emits a low-confidence "Something is downloading" entry
 *     so the pill shows at least a generic indicator.
 *
 * Confidence: 0.3 (traffic-only guess with no title or real progress).
 *
 * The aggregator suppresses this source's own entry whenever a higher-
 * confidence source is already showing something for the same period.
 *
 * ── Hysteresis ───────────────────────────────────────────────────────────────
 * RX speed is bursty even during a genuine sustained download — TCP
 * slow-start, brief stalls between chunks/listener round-trips, etc. A naive
 * "speed >= threshold ? show : clear" check flaps the entry on/off almost
 * every tick, which upstream causes Phs3Manager to register/unregister
 * rapidly. To fix this we require [IDLE_TICKS_TO_CLEAR] consecutive
 * below-threshold ticks before actually clearing an existing entry — a
 * single slow tick no longer kills it.
 */
class TrafficStatsDownloadSource(
    private val scope: CoroutineScope,
) : DownloadSource {

    override val name = "TrafficStats"

    private val _updates = MutableStateFlow<Map<String, AggregatedDownload>>(emptyMap())
    override val updates: Flow<Map<String, AggregatedDownload>> = _updates.asStateFlow()

    private var pollJob: Job? = null

    /** Expose live speed so the aggregator can inject it into richer sources. */
    var currentRxSpeedBps: Long = 0L
        private set

    /** Bytes/s threshold below which we treat network as idle. ~50 KB/s */
    private val SPEED_THRESHOLD_BPS = 50 * 1_024L
    private val POLL_MS = 1_000L

    /**
     * How many consecutive below-threshold ticks are required before we
     * actually clear an already-active entry. At POLL_MS = 1000L this is a
     * ~3s grace window — long enough to ride out a brief stall, short
     * enough that the pill still disappears promptly once a download
     * genuinely finishes or stalls for real.
     */
    private val IDLE_TICKS_TO_CLEAR = 3

    private var consecutiveIdleTicks = 0

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTimeMs  = System.currentTimeMillis()

    override fun start() {
        pollJob = scope.launch {
            while (true) {
                delay(POLL_MS)
                tick()
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        currentRxSpeedBps = 0L
        consecutiveIdleTicks = 0
        _updates.value = emptyMap()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun tick() {
        val nowRx   = TrafficStats.getTotalRxBytes()
        val nowTime = System.currentTimeMillis()
        val elapsed = (nowTime - lastTimeMs).coerceAtLeast(1L)

        val speed = ((nowRx - lastRxBytes) * 1000L / elapsed).coerceAtLeast(0L)
        currentRxSpeedBps = speed

        lastRxBytes = nowRx
        lastTimeMs  = nowTime

        if (speed >= SPEED_THRESHOLD_BPS) {
            // Back above threshold — reset the idle counter and (re)publish
            // immediately. No hysteresis needed on the "appearing" edge; we
            // only debounce the "disappearing" edge, since a fast-appearing
            // pill feels responsive while a fast-disappearing one feels
            // flickery.
            consecutiveIdleTicks = 0
            _updates.value = mapOf(
                "traffic:global" to AggregatedDownload(
                    sourceId   = name,
                    key        = "traffic:global",
                    info       = DownloadInfo(
                        title            = "Downloading…",
                        progressFraction = 0f,          // indeterminate
                        bytesDownloaded  = 0L,
                        totalBytes       = null,
                        etaMs            = null,
                        networkType      = DownloadNetworkType.UNKNOWN,
                        speedBps         = speed,
                    ),
                    confidence = 0.3f,
                )
            )
        } else {
            // Below threshold — only clear after IDLE_TICKS_TO_CLEAR
            // consecutive low ticks. If we're already empty, this is a
            // cheap no-op; if an entry is showing, we ride out brief stalls
            // instead of yanking it away every time the network blips.
            if (_updates.value.isEmpty()) {
                consecutiveIdleTicks = 0
                return
            }
            consecutiveIdleTicks++
            if (consecutiveIdleTicks >= IDLE_TICKS_TO_CLEAR) {
                consecutiveIdleTicks = 0
                _updates.value = emptyMap()
            }
            // else: keep the last published entry as-is (stale speed value
            // is fine for ~3s — the aggregator will catch up next tick).
        }
    }
}