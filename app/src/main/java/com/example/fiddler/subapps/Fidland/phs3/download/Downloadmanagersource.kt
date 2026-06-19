package com.example.fiddler.subapps.Fidland.phs3.download

import android.app.DownloadManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Source 2 — Android DownloadManager.
 *
 * Covers: Play Store app updates, system OTA chunks, any app that calls
 * DownloadManager.enqueue() — notably NOT Chrome (it has its own engine).
 *
 * Polls every [POLL_MS] while running. Polling is cheap: DownloadManager
 * uses a SQLite cursor under the hood — typically < 1ms for small queues.
 *
 * Confidence: 0.9 — has exact byte counts and title, but no speed.
 */
class DownloadManagerSource(
    context: Context,
    private val scope: CoroutineScope,
) : DownloadSource {

    override val name = "DownloadManager"

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _updates = MutableStateFlow<Map<String, AggregatedDownload>>(emptyMap())
    override val updates: Flow<Map<String, AggregatedDownload>> = _updates.asStateFlow()

    private var pollJob: Job? = null
    private val POLL_MS = 2_000L

    override fun start() {
        pollJob = scope.launch {
            while (true) {
                tick()
                delay(POLL_MS)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        _updates.value = emptyMap()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun tick() {
        val found = mutableMapOf<String, AggregatedDownload>()

        try {
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED
            )
            val cursor = dm.query(query) ?: return
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id    = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                        ?.takeIf { it.isNotBlank() } ?: "Downloading…"
                    val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        .takeIf { it > 0 }

                    val fraction = if (total != null) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val key = "dm:$id"

                    found[key] = AggregatedDownload(
                        sourceId   = name,
                        key        = key,
                        info       = DownloadInfo(
                            title            = title,
                            progressFraction = fraction,
                            bytesDownloaded  = bytes,
                            totalBytes       = total,
                            etaMs            = null,
                            networkType      = DownloadNetworkType.UNKNOWN,
                            speedBps         = null,
                        ),
                        confidence = 0.9f,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadManagerSource", "query failed: ${e.message}")
        }

        _updates.value = found
    }
}