package com.example.fiddler.subapps.Fidland.phs3.download

import kotlinx.coroutines.flow.Flow

/**
 * Common interface every download source must implement.
 *
 * Each source emits a map of active downloads keyed by a stable string ID
 * unique within that source (e.g. "$pkg:$notifId", "dm:$downloadId",
 * "traffic:global"). The aggregator merges all source maps into one.
 *
 * Emitting an empty map means this source sees no active downloads.
 */
interface DownloadSource {
    /** Human-readable name for debugging (e.g. "Notification", "DownloadManager"). */
    val name: String

    /** Cold flow — the aggregator calls start() then collects this. */
    val updates: Flow<Map<String, AggregatedDownload>>

    fun start()
    fun stop()
}

/**
 * A richer download model used inside the aggregator, carrying the source
 * name and a confidence level so the aggregator can apply priority rules.
 *
 * @param sourceId     Which [DownloadSource] produced this entry.
 * @param key          Stable unique key within the source.
 * @param info         The user-facing download snapshot.
 * @param confidence   How certain this source is (0f–1f). Used to prefer
 *                     richer sources (notification > DownloadManager > traffic).
 * @param timestampMs  When this entry was last updated — stale entries are pruned.
 */
data class AggregatedDownload(
    val sourceId: String,
    val key: String,
    val info: DownloadInfo,
    val confidence: Float,       // 1.0 = certain (has title + progress), 0.3 = traffic-only guess
    val timestampMs: Long = System.currentTimeMillis(),
)