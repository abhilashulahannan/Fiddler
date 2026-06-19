package com.example.fiddler.subapps.Fidland.phs3.download

import android.os.FileObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Source 4 — FileObserver on the Downloads folder.
 *
 * Covers: completion detection. When a file appears / finishes writing in
 * /storage/emulated/0/Download/, we know a download just finished.
 *
 * This source does NOT track in-progress downloads (the file doesn't exist
 * yet). Its role is to:
 *   1. Briefly flash a "Download complete" state (auto-clears after [FLASH_MS]).
 *   2. Confirm that a download the NotificationSource was tracking is now done,
 *      so the aggregator can clean it up.
 *
 * Confidence: 0.8 — we know a file arrived, but we have no speed or progress.
 *
 * Note: CLOSE_WRITE fires when the OS finishes writing the file — that's the
 * reliable "done" signal. CREATE fires too early (the file is empty/partial).
 */
class FileObserverDownloadSource(
    private val scope: CoroutineScope,
) : DownloadSource {

    override val name = "FileObserver"

    private val _updates = MutableStateFlow<Map<String, AggregatedDownload>>(emptyMap())
    override val updates: Flow<Map<String, AggregatedDownload>> = _updates.asStateFlow()

    /** How long to show "Download complete" before auto-clearing. */
    private val FLASH_MS = 3_000L

    private var observer: FileObserver? = null
    private var flashJob: Job? = null

    private val downloadsPath = "/storage/emulated/0/Download"

    override fun start() {
        val dir = File(downloadsPath)
        if (!dir.exists()) return

        observer = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (event == CLOSE_WRITE || event == MOVED_TO) {
                    onFileArrived(path)
                }
            }
        }
        observer?.startWatching()
    }

    override fun stop() {
        observer?.stopWatching()
        observer = null
        flashJob?.cancel()
        _updates.value = emptyMap()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun onFileArrived(filename: String) {
        val key  = "file:$filename"
        val size = File(downloadsPath, filename).length().takeIf { it > 0 }

        _updates.value = mapOf(
            key to AggregatedDownload(
                sourceId   = name,
                key        = key,
                info       = DownloadInfo(
                    title            = filename,
                    progressFraction = 1f,      // 100% — it just finished
                    bytesDownloaded  = size ?: 0L,
                    totalBytes       = size,
                    etaMs            = 0L,
                    networkType      = DownloadNetworkType.UNKNOWN,
                    speedBps         = null,
                ),
                confidence = 0.8f,
            )
        )

        // Auto-clear after flash duration so the pill doesn't stick on "complete"
        flashJob?.cancel()
        flashJob = scope.launch {
            delay(FLASH_MS)
            _updates.value = emptyMap()
        }
    }
}