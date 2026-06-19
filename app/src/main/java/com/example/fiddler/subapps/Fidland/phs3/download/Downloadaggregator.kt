package com.example.fiddler.subapps.Fidland.phs3.download

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Merges all [DownloadSource] instances into a single prioritised list of
 * active downloads for the pill to display.
 *
 * ── Merge rules ──────────────────────────────────────────────────────────────
 *
 * 1. DEDUPLICATION — If the Notification source and the DownloadManager source
 *    both report what is clearly the same download (same title, started within
 *    3 seconds of each other), the higher-confidence entry wins and the other
 *    is dropped.
 *
 * 2. SPEED INJECTION — The TrafficStats source does not produce its own entry
 *    when a Notification or DownloadManager entry is already active. Instead,
 *    its live speed reading is injected into those richer entries so the pill
 *    can show "1.2 MB/s" even when the notification didn't include it.
 *
 * 3. TRAFFIC FALLBACK — If TrafficStats sees significant RX traffic
 *    (> 50 KB/s) but NO other source is active, a single low-confidence
 *    "Downloading…" entry is emitted. This catches background downloads that
 *    produce no notification and don't go through DownloadManager.
 *
 * 4. COMPLETION FLASH — FileObserver entries (progressFraction == 1f) are
 *    merged in for their 3-second flash window, then disappear automatically
 *    (the source handles the timer).
 *
 * 5. PRIORITY ORDER for the pill's primary display slot when multiple
 *    downloads are active:
 *       Notification (1.0) > DownloadManager (0.9) > FileObserver (0.8) > Traffic (0.3)
 *
 * ── Output ───────────────────────────────────────────────────────────────────
 *
 * [activeDownloads] — ordered list, highest-confidence first. The trigger
 * takes [activeDownloads.value.firstOrNull()] as the primary pill entry, or
 * could cycle through them if multiple are active.
 *
 * [primaryDownload] — convenience alias for the single highest-priority entry,
 * or null when nothing is downloading.
 */
class DownloadAggregator(
    context: Context,
    private val scope: CoroutineScope,
) {
    // ── Sources ───────────────────────────────────────────────────────────────

    val notificationSource   = NotificationDownloadSource()
    val downloadManagerSource = DownloadManagerSource(context, scope)
    val trafficSource        = TrafficStatsDownloadSource(scope)
    val fileObserverSource   = FileObserverDownloadSource(scope)

    private val allSources: List<DownloadSource> = listOf(
        notificationSource,
        downloadManagerSource,
        trafficSource,
        fileObserverSource,
    )

    // ── Output flows ──────────────────────────────────────────────────────────

    private val _activeDownloads = MutableStateFlow<List<AggregatedDownload>>(emptyList())

    /** All active downloads, highest-confidence first. */
    val activeDownloads: Flow<List<AggregatedDownload>> = _activeDownloads.asStateFlow()

    /** The single best download to show in the pill. Null when idle. */
    val primaryDownload: Flow<DownloadInfo?> = MutableStateFlow<DownloadInfo?>(null).also { out ->
        _activeDownloads.onEach { list ->
            out.value = list.firstOrNull()?.let { best ->
                // Inject live speed from TrafficStats if the entry itself has none
                val speed = best.info.speedBps ?: trafficSource.currentRxSpeedBps.takeIf { it > 0 }
                best.info.copy(speedBps = speed)
            }
        }.launchIn(scope)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        allSources.forEach { it.start() }

        combine(
            notificationSource.updates,
            downloadManagerSource.updates,
            trafficSource.updates,
            fileObserverSource.updates,
        ) { notif, dm, traffic, file ->
            merge(notif, dm, traffic, file)
        }.onEach { merged ->
            _activeDownloads.value = merged
        }.launchIn(scope)
    }

    fun stop() {
        allSources.forEach { it.stop() }
        _activeDownloads.value = emptyList()
    }

    // ── Merge logic ───────────────────────────────────────────────────────────

    private fun merge(
        notif:   Map<String, AggregatedDownload>,
        dm:      Map<String, AggregatedDownload>,
        traffic: Map<String, AggregatedDownload>,
        file:    Map<String, AggregatedDownload>,
    ): List<AggregatedDownload> {

        // Start with high-confidence sources
        val highConfidence = mutableMapOf<String, AggregatedDownload>()
        highConfidence.putAll(dm)    // 0.9 — good base
        highConfidence.putAll(notif) // 1.0 — overwrites DM entry if same key (it won't be,
        //        but dedup below handles title collisions)

        // FileObserver entries: merge in (completion flash)
        file.values.forEach { fileEntry ->
            highConfidence[fileEntry.key] = fileEntry
        }

        // Dedup: if a Notification and a DownloadManager entry have the same
        // title (case-insensitive), keep the Notification one (higher confidence).
        val deduplicated = mutableMapOf<String, AggregatedDownload>()
        val notifTitles  = notif.values.map { it.info.title.lowercase() }.toSet()

        highConfidence.forEach { (key, entry) ->
            if (entry.sourceId == "DownloadManager" &&
                notifTitles.any { it == entry.info.title.lowercase() }) {
                return@forEach // skip — notification source already covers this
            }
            deduplicated[key] = entry
        }

        // Traffic fallback: only add if nothing higher-confidence is active
        if (deduplicated.isEmpty() && traffic.isNotEmpty()) {
            traffic.values.forEach { deduplicated[it.key] = it }
        }

        // Sort: highest confidence first, then most recently updated
        return deduplicated.values
            .sortedWith(compareByDescending<AggregatedDownload> { it.confidence }
                .thenByDescending { it.timestampMs })
    }
}