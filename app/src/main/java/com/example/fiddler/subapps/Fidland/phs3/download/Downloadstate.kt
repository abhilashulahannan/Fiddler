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
 * @param networkType     The connection the download is using, for the icon.
 * @param speedBps        Current download speed in bytes/second, or null.
 */
data class DownloadInfo(
    val title: String,
    val progressFraction: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val etaMs: Long?,
    val networkType: DownloadNetworkType = DownloadNetworkType.UNKNOWN,
    val speedBps: Long? = null
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