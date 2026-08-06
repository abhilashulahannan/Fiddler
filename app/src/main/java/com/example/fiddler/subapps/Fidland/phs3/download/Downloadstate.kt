package com.example.fiddler.subapps.Fidland.phs3.download

/**
 * Phs3 module — Download — shared state models.
 *
 * Wiring (future): populate [DownloadInfo] from Android's [DownloadManager]
 * (query [DownloadManager.Query]) or from a custom download engine.
 * A future DownloadPhs3Trigger should call `activatePhs3(DownloadPhs3Handler(…))`
 * when a download starts / resumes, and `deactivatePhs3()` once it completes
 * or is cancelled — mirroring the AlarmPhs3Trigger / MusicPhs3Trigger pattern.
 */

/** The type of network currently carrying the download. */
enum class DownloadNetworkType {
    WIFI,
    CELLULAR_3G,
    CELLULAR_4G,
    CELLULAR_5G,
    UNKNOWN
}

/**
 * Snapshot of one active (or recently completed) download.
 *
 * @param title           Human-readable file/task name, e.g. "update.apk".
 * @param progressFraction Download progress in [0f, 1f]. 1f = complete.
 * @param bytesDownloaded Bytes received so far.
 * @param totalBytes      Total expected bytes. Null if the server didn't
 *                         send Content-Length (indeterminate progress).
 * @param etaMs           Estimated milliseconds remaining, or null if unknown.
 *                         §B7 Phase 4: real sources leave this null (see
 *                         [DownloadInfo.derivedEtaMs] for the actual State5
 *                         derivation) — kept here for FileObserver's
 *                         completion-flash `0L`.
 * @param networkType     Carried for source compatibility only — §B7 Phase 4
 *                         retires the network-type icon path entirely (every
 *                         source has always reported [DownloadNetworkType
 *                         .UNKNOWN] here; no source was ever fixed to report
 *                         real values, and the design doc resolved this as
 *                         "not a fix-first blocker" — location-a now shows a
 *                         generic download glyph instead, see
 *                         [DownloadPhs3Handler]). Unused by any renderer.
 * @param speedBps        Current download speed in bytes/second, or null.
 * @param packageName     §B7 Phase 4 — the source app's package, when known.
 *                         Only [NotificationDownloadSource] can populate this
 *                         (via `sbn.packageName`); the other 3 sources have
 *                         no attribution path, so this is null for them —
 *                         see [DownloadPhs3Handler.State5Content]'s
 *                         "unknown app" fallback.
 */
data class DownloadInfo(
    val title: String,
    val progressFraction: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val etaMs: Long?,
    val networkType: DownloadNetworkType = DownloadNetworkType.UNKNOWN,
    val speedBps: Long? = null,
    val packageName: String? = null,
)

// ── Formatting helpers ────────────────────────────────────────────────────────

/**
 * Formats [etaMs] as a compact human-readable string for the indicator:
 *   < 60 s   → "42s"
 *   < 60 min → "14m"
 *   otherwise → "2h 5m"
 * Returns "—" if null / unknown.
 */
fun formatEta(etaMs: Long?): String {
    if (etaMs == null || etaMs < 0) return "—"
    val totalSecs = etaMs / 1000L
    if (totalSecs < 60) return "${totalSecs}s"
    val totalMins = totalSecs / 60
    if (totalMins < 60) return "${totalMins}m"
    val h = totalMins / 60
    val m = totalMins % 60
    return if (m == 0L) "${h}h" else "${h}h ${m}m"
}

/**
 * Formats bytes as a compact size string:
 *   < 1 KB  → "512 B"
 *   < 1 MB  → "340 KB"
 *   < 1 GB  → "12.3 MB"
 *   otherwise → "1.2 GB"
 */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L              -> "${bytes} B"
    bytes < 1_048_576L          -> "${bytes / 1_024} KB"
    bytes < 1_073_741_824L      -> "%.1f MB".format(bytes / 1_048_576.0)
    else                        -> "%.1f GB".format(bytes / 1_073_741_824.0)
}

/**
 * Formats download speed as "X.X MB/s", "XX KB/s", etc.
 * Returns null if [speedBps] is null or zero.
 */
fun formatSpeed(speedBps: Long?): String? {
    if (speedBps == null || speedBps <= 0) return null
    return when {
        speedBps < 1_024L         -> "${speedBps} B/s"
        speedBps < 1_048_576L     -> "${speedBps / 1_024} KB/s"
        else                      -> "%.1f MB/s".format(speedBps / 1_048_576.0)
    }
}

/**
 * Returns true while the download is still in progress (not yet complete).
 */
fun DownloadInfo.isActive(): Boolean = progressFraction < 1f

/**
 * §B7 Phase 4 — State5's real ETA derivation, `(totalBytes -
 * bytesDownloaded) / speedBps`, resolving the doc's flag ("needs real
 * derivation... feasible when both inputs exist, not derivable for the
 * Traffic-only fallback"). Returns null whenever either input is missing —
 * that's exactly the Traffic-only case ([totalBytes] is always null there —
 * see [TrafficStatsDownloadSource]) and any indeterminate-progress entry.
 * Distinct from [DownloadInfo.etaMs], which no real source ever populates
 * except FileObserver's completion-flash `0L`.
 */
fun DownloadInfo.derivedEtaMs(): Long? {
    val total = totalBytes ?: return null
    val speed = speedBps?.takeIf { it > 0 } ?: return null
    val remaining = (total - bytesDownloaded).coerceAtLeast(0L)
    return (remaining * 1000L) / speed
}

/**
 * §B7 Phase 4 State5 "which-app" block — resolves [packageName] to an
 * installed app's display label (e.g. "Chrome"), or null if unresolvable.
 * Only ever non-null for [NotificationDownloadSource] entries — see
 * [DownloadInfo.packageName]'s doc. Callers show an "Unknown app" fallback
 * when this returns null, per the doc's adopted resolution of that open
 * question (a field-only-when-available reading was the other option
 * considered — an explicit fallback string reads better in State5's fixed
 * layout than a block that sometimes silently vanishes).
 */
fun DownloadInfo.resolveAppLabel(context: android.content.Context): String? {
    val pkg = packageName ?: return null
    return try {
        context.packageManager
            .getApplicationInfo(pkg, 0)
            .loadLabel(context.packageManager)
            .toString()
    } catch (_: Exception) {
        null
    }
}