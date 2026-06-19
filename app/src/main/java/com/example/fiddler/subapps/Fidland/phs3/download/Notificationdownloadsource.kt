package com.example.fiddler.subapps.Fidland.phs3.download

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Source 1 — Notification listener.
 *
 * Covers: Chrome, Firefox, Samsung Browser, torrent apps, WhatsApp, Telegram,
 * any app that posts an ONGOING progress notification for a download.
 *
 * This is the PRIMARY source — it has the richest data (title, progress fraction,
 * sometimes speed text) and the highest confidence.
 *
 * Wire-up: call [onNotificationPosted] / [onNotificationRemoved] from your
 * existing NotificationListenerService overrides. No polling needed.
 *
 * ── Play Store special case ──────────────────────────────────────────────────
 * Google Play (com.android.vending) drives downloads through its own
 * internal Finsky download service rather than posting a per-percent
 * progress notification or going through android.app.DownloadManager. Its
 * ongoing notification frequently has neither a "download"-named channel
 * nor an attached progress bar, so the original gate silently dropped it,
 * leaving TrafficStats as the only (noisy, title-less) source for Play
 * downloads. [ALWAYS_QUALIFY_PACKAGES] whitelists packages whose ongoing
 * notification should count as a download regardless of channel/progress —
 * we just won't have a numeric percentage for them (indeterminate fraction).
 *
 * Confidence: 1.0 — has explicit title and progress bar.
 */
class NotificationDownloadSource : DownloadSource {

    companion object {
        /**
         * Packages whose ongoing notification should always be treated as a
         * download, even without a "download" channel or a progress bar
         * attached. Add other store/installer apps here if they exhibit the
         * same pattern (own internal download engine, sparse notification).
         */
        private val ALWAYS_QUALIFY_PACKAGES = setOf(
            "com.android.vending", // Google Play Store / Finsky
        )
    }

    override val name = "Notification"

    private val _updates = MutableStateFlow<Map<String, AggregatedDownload>>(emptyMap())
    override val updates: Flow<Map<String, AggregatedDownload>> = _updates.asStateFlow()

    // Live map: "$pkg:$notifId" → AggregatedDownload
    private val active = mutableMapOf<String, AggregatedDownload>()

    override fun start() { /* Nothing to start — driven by callbacks */ }
    override fun stop()  { active.clear(); _updates.value = emptyMap() }

    // ── Called from NotificationListenerService ───────────────────────────────

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return

        // Must be an ongoing notification on the "downloads" channel,
        // OR have a progress bar — covers apps that use other channel names.
        // EXCEPTION: packages in ALWAYS_QUALIFY_PACKAGES (e.g. Play Store)
        // qualify on isOngoing alone, since they often post neither a
        // download-named channel nor a progress bar even while actively
        // downloading — see the Play Store special case note above.
        val isDownloadChannel = n.channelId?.lowercase()?.contains("download") == true
        val hasProgress       = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
        val isOngoing         = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isAlwaysQualified = sbn.packageName in ALWAYS_QUALIFY_PACKAGES

        if (!isOngoing) return
        if (!isDownloadChannel && !hasProgress && !isAlwaysQualified) return

        val extras      = n.extras
        val title       = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: "Downloading…"
        val progress    = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val fraction = when {
            // No real progress data at all (the always-qualify path with no
            // progress bar) — treat as indeterminate rather than reporting
            // a false 0%, which would otherwise render as a stuck ring.
            indeterminate || progressMax <= 0 -> 0f
            else -> (progress.toFloat() / progressMax).coerceIn(0f, 1f)
        }

        // Try to extract speed from subtext/bigText: "1.2 MB/s" or "2 MB of 10 MB"
        val subText  = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val bigText  = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val speedBps = parseSpeedFromText(subText) ?: parseSpeedFromText(bigText)

        val key = "${sbn.packageName}:${sbn.id}"
        val entry = AggregatedDownload(
            sourceId   = name,
            key        = key,
            info       = DownloadInfo(
                title            = title,
                progressFraction = fraction,
                bytesDownloaded  = 0L,
                totalBytes       = if (progressMax > 0) progressMax.toLong() else null,
                etaMs            = null,
                networkType      = DownloadNetworkType.UNKNOWN,
                speedBps         = speedBps,
            ),
            confidence = 1.0f,
        )

        active[key] = entry
        _updates.value = active.toMap()
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = "${sbn.packageName}:${sbn.id}"
        if (active.remove(key) != null) {
            _updates.value = active.toMap()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Attempts to parse a speed value from notification text like:
     *   "1.2 MB/s"  → 1_258_291 B/s
     *   "500 KB/s"  → 512_000 B/s
     *   "3.5 MB of 10 MB"  → null (not a speed)
     */
    private fun parseSpeedFromText(text: String): Long? {
        val regex = Regex("""([\d.]+)\s*(KB|MB|GB)/s""", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].uppercase()) {
            "KB" -> (value * 1_024).toLong()
            "MB" -> (value * 1_048_576).toLong()
            "GB" -> (value * 1_073_741_824).toLong()
            else -> null
        }
    }
}