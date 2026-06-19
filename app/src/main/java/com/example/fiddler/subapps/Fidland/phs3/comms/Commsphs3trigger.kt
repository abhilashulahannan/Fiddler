package com.example.fiddler.subapps.Fidland.phs3.comms

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * CommsPhs3Trigger
 *
 * Drives the Comms phs3 slot from [CommsAggregator], which merges Bluetooth,
 * WiFi, NFC, and Cellular state into one [CommsSnapshot].
 *
 * Activates whenever [CommsSnapshot.hasAnythingToShow] is true — which in
 * practice is almost always (cellular service alone qualifies on any phone
 * with a SIM), so this behaves less like an event-driven trigger (Alarm,
 * Flashlight) and more like an always-on status slot. Consider gating this
 * behind a "phs3_comms" preference toggle the same way Flashlight/Alarm/Nav
 * are gated in FidlandService.onCreate, since users may not want a near-
 * permanent phs3 occupant.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var commsTrigger: CommsPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       commsTrigger = CommsPhs3Trigger(applicationContext, serviceScope, this)
 *       if (prefs.getBoolean("phs3_comms", false))
 *           commsTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       commsTrigger.stop()
 *       ...
 *   }
 */
class CommsPhs3Trigger(
    context: android.content.Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    val aggregator = CommsAggregator(context, scope)

    private var collectJob: Job? = null

    fun start() {
        Phs3DebugLog.onTriggerStart("Comms")
        aggregator.start()

        collectJob = aggregator.snapshot
            .onEach { snapshot ->
                if (snapshot.hasAnythingToShow()) {
                    service.activatePhs3(CommsPhs3Handler(snapshot))
                } else {
                    service.deactivatePhs3("Comms")
                }
            }
            .launchIn(scope)
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Comms")
        collectJob?.cancel()
        collectJob = null
        aggregator.stop()
        service.deactivatePhs3("Comms")
    }
}