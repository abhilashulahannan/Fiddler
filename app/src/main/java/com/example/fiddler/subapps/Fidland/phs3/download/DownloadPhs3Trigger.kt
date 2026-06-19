package com.example.fiddler.subapps.Fidland.phs3.download

import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * DownloadPhs3Trigger — replaces the old polling-only version.
 *
 * Now driven by [DownloadAggregator] which merges four sources:
 *   1. NotificationDownloadSource  — Chrome, Firefox, WhatsApp, etc.
 *   2. DownloadManagerSource       — Play Store, system OTA
 *   3. TrafficStatsDownloadSource  — catch-all speed signal / fallback
 *   4. FileObserverDownloadSource  — completion detection
 *
 * Wire-up (already done in FidlandService):
 *   downloadTrigger = DownloadPhs3Trigger(this, serviceScope, this)
 *   downloadTrigger.start()
 *
 * Wire-up for NotificationListenerService (add these two lines):
 *   override fun onNotificationPosted(sbn) {
 *       downloadTrigger?.notificationSource?.onNotificationPosted(sbn)  // ← add
 *       ...
 *   }
 *   override fun onNotificationRemoved(sbn) {
 *       downloadTrigger?.notificationSource?.onNotificationRemoved(sbn) // ← add
 *       ...
 *   }
 *
 * The trigger exposes [aggregator.notificationSource] so the
 * NotificationListenerService can feed it without needing a reference to
 * the whole trigger.
 */
class DownloadPhs3Trigger(
    context: android.content.Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    val aggregator = DownloadAggregator(context, scope)

    /** Convenience accessor for NotificationListenerService wiring. */
    val notificationSource: NotificationDownloadSource
        get() = aggregator.notificationSource

    private var collectJob: Job? = null

    fun start() {
        aggregator.start()

        collectJob = aggregator.primaryDownload
            .onEach { info ->
                if (info != null) {
                    service.activatePhs3(DownloadPhs3Handler(info))
                } else {
                    service.deactivatePhs3("Download")
                }
            }
            .launchIn(scope)
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        aggregator.stop()
        service.deactivatePhs3("Download")
    }
}