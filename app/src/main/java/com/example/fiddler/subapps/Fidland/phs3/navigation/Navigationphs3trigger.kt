package com.example.fiddler.subapps.Fidland.phs3.navigation

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * NavigationPhs3Trigger
 *
 * Watches [NavigationRepository.flow] and activates / deactivates the
 * Navigation phs3 slot on FidlandService.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var navigationTrigger: NavigationPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       navigationTrigger = NavigationPhs3Trigger(serviceScope, this)
 *       navigationTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       navigationTrigger.stop()
 *       ...
 *   }
 *
 * ── Location-a wiring ─────────────────────────────────────────────────────────
 * Place [NavigationPhs3Handler.LocationAIndicator] in the pill's left-zone
 * composable (location a), the same slot used by AlbumArtSpinner for music:
 *
 *   if (activePhs3Handler is NavigationPhs3Handler) {
 *       (activePhs3Handler as NavigationPhs3Handler).LocationAIndicator()
 *   }
 *
 * ── Debugging ────────────────────────────────────────────────────────────────
 * Logs to Phs3DebugLog (visible in the Debugging screen): trigger start/stop,
 * and one POLL entry per NavigationRepository.flow emission showing whether
 * navigation is active, how many upcoming steps are queued, and the next
 * manoeuvre/ETA — useful for confirming Maps notifications are being parsed.
 */
class NavigationPhs3Trigger(
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private val handler = NavigationPhs3Handler()
    private var watchJob: Job? = null

    fun start() {
        Phs3DebugLog.onTriggerStart("Navigation")
        watchJob = scope.launch {
            NavigationRepository.flow.collect { snapshot ->
                val nextLabel = snapshot.nextStep?.instruction ?: "none"
                Phs3DebugLog.onPoll(
                    "Navigation",
                    "active=${snapshot.isActive} steps=${snapshot.steps.size} " +
                            "next=\"$nextLabel\" eta=${snapshot.etaText}"
                )

                if (snapshot.isActive) {
                    service.activatePhs3(handler)
                } else {
                    service.deactivatePhs3("Navigation")
                }
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Navigation")
        watchJob?.cancel()
        watchJob = null
        NavigationRepository.onNavigationEnded()
    }
}