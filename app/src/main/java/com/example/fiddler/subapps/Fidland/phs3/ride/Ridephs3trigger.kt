package com.example.fiddler.subapps.Fidland.phs3.ride

import android.annotation.SuppressLint
import android.content.Context
import com.example.fiddler.subapps.Fidland.NotificationListenerService
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RidePhs3Trigger
 *
 * Watches [RideRepository.flow] and:
 *  • activates / deactivates the Ride phs3 slot on FidlandService
 *  • submits/withdraws this entity's [Phs3Priority] bid (see §B9 Ride pass below)
 *  • drives GPS polling cadence: every 10s in PRE_RIDE, every 15s in IN_RIDE,
 *    stopped entirely once IDLE/ENDED
 *
 * No manual input anywhere in this flow — pickup is captured automatically
 * from the first GPS fix after a ride is detected; destination comes from
 * notification scraping (RideRepository) or stays unresolved, in which case
 * the pill simply omits the progress circle (see RideSnapshot.hasProgress).
 *
 * ── §B9 Ride pass — from "not wired" to a real bid ──────────────────────────
 * Previous version only called [FidlandService.activatePhs3]/
 * [FidlandService.deactivatePhs3] — no [Phs3Priority] was ever submitted, the
 * `phs3_ride` settings toggle was never read, and this trigger was never
 * constructed by [FidlandService]. This pass builds the rest of §B7's
 * "deliberately minimal" decision:
 *
 * **Class — home Submissive/10, escalating to Dominant/[DOMINANT_SUB_SCORE]
 * (60, confirmed in §B7's Dominant-tier table) while qualified, no separate
 * threshold/sub-condition** — same "only ever registers while active" shape
 * as Call/Alarm/Timer/Navigation (see [NavigationPhs3Trigger]'s own class
 * doc): the nominal Submissive/10 tier is never actually observed as a live
 * bid, since qualification (any non-IDLE phase) and Dominant/60 are the same
 * event. So this trigger only ever submits the Dominant bid, never a
 * Submissive one — there's nothing to escalate *from*.
 *
 * **Special Condition — new, not doc-specified.** §B7 explicitly deferred
 * this ("none defined... not enough reliable signal yet to define a
 * promotion condition with confidence"), reasoning from the *absence* of a
 * narrower distinguishing signal (e.g. driver-en-route vs. driver-arrived).
 * That reasoning doesn't actually block a transition-based promotion, though
 * — [RidePhase] changes themselves (IDLE→PRE_RIDE "ride booked", PRE_RIDE→
 * IN_RIDE "ride started", any phase→ENDED "ride ended") are already reliably
 * detected by [RideRepository]/[inferPhase], the same shape of signal
 * Football's match-status-transition detector and Navigation's new-direction
 * trigger both promote on. So: any [RidePhase] change while non-IDLE fires a
 * Special Condition pulse, sub-score [SPECIAL_CONDITION_SUB_SCORE] (55 —
 * picked, not signed off; sits between Football's match-event tier (50) and
 * Battery's threshold-crossing tier (60), in the same informal "5-8s tier
 * band" Navigation/Music/Weather/Comms share per §B6's shared-dwell note),
 * for [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS], falling back to
 * the standing Dominant/60 bid — same shape as Weather's/Navigation's
 * dwell-and-fallback promotions. Revisit if/when a narrower distinguishing
 * signal (§B7's driver-en-route/-arrived example) makes a different
 * promotion condition worth adding alongside this one.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────
 *
 *   private lateinit var rideTrigger: RidePhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       rideTrigger = RidePhs3Trigger(applicationContext, serviceScope, this)
 *       if (prefs.getBoolean("phs3_ride", false))
 *           rideTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       rideTrigger.stop()
 *       ...
 *   }
 *
 * ── Notification wiring ─────────────────────────────────────────────────────
 * [start]/[stop] flip [NotificationListenerService.rideEnabled], which gates
 * the [RideRepository.onNotification]/[RideRepository.onNotificationRemoved]
 * calls added to [NotificationListenerService.onNotificationPosted]/
 * [onNotificationRemoved] this pass — previously nothing called them at all.
 */
class RidePhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    companion object {
        private const val PRE_RIDE_POLL_MS = 10_000L
        private const val IN_RIDE_POLL_MS  = 15_000L
        /** How long ENDED is shown before the repository resets and we deregister. */
        private const val ENDED_LINGER_MS  = 4_000L

        /** §B7 Dominant tier, confirmed value — "Ride-any-active-phase". */
        private const val DOMINANT_SUB_SCORE = 60

        /** Picked default for the Special Condition §B7 left undefined — see class doc. */
        private const val SPECIAL_CONDITION_SUB_SCORE = 55
    }

    private val handler = RidePhs3Handler()
    private var watchJob: Job? = null
    private var pollJob: Job? = null

    /** Special-Condition dwell timer for phase-transition pulses — see class doc. */
    private var dwellJob: Job? = null
    /** ENDED's own linger-then-reset timer, tracked so [stop] can cancel it cleanly. */
    private var endedLingerJob: Job? = null

    /** Last-seen phase, for edge-detecting transitions (see class doc's Special Condition). */
    private var previousPhase: RidePhase = RidePhase.IDLE

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private var locationCallback: LocationCallback? = null
    private var currentPollIntervalMs: Long = -1L

    fun start() {
        Phs3DebugLog.onTriggerStart("Ride")
        RideRepository.attachScope(scope)
        NotificationListenerService.rideEnabled = true

        watchJob = scope.launch {
            RideRepository.flow.collect { snapshot -> onSnapshot(snapshot) }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Ride")
        NotificationListenerService.rideEnabled = false
        watchJob?.cancel()
        watchJob = null
        dwellJob?.cancel()
        dwellJob = null
        endedLingerJob?.cancel()
        endedLingerJob = null
        previousPhase = RidePhase.IDLE
        stopLocationUpdates()
        service.phs3Manager.scheduler.withdraw(handler.label)
        service.deactivatePhs3(handler.label)
        RideRepository.reset()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun onSnapshot(snapshot: RideSnapshot) {
        val phase = snapshot.phase
        val transitioned = phase != previousPhase

        when (phase) {
            RidePhase.IDLE -> {
                dwellJob?.cancel()
                dwellJob = null
                endedLingerJob?.cancel()
                endedLingerJob = null
                stopLocationUpdates()
                service.phs3Manager.scheduler.withdraw(handler.label)
                service.deactivatePhs3(handler.label)
            }

            RidePhase.PRE_RIDE -> {
                service.activatePhs3(handler)
                ensurePolling(PRE_RIDE_POLL_MS)
                submitDominantOrPulse(phase, transitioned)
            }

            RidePhase.IN_RIDE -> {
                service.activatePhs3(handler)
                ensurePolling(IN_RIDE_POLL_MS)
                RideRepository.fuseFromNavigationIfNeeded()
                submitDominantOrPulse(phase, transitioned)
            }

            RidePhase.ENDED -> {
                stopLocationUpdates()
                service.activatePhs3(handler) // brief lingering display
                submitDominantOrPulse(phase, transitioned)
                if (endedLingerJob == null) {
                    endedLingerJob = scope.launch {
                        delay(ENDED_LINGER_MS)
                        dwellJob?.cancel()
                        dwellJob = null
                        service.phs3Manager.scheduler.withdraw(handler.label)
                        service.deactivatePhs3(handler.label)
                        RideRepository.reset()
                    }
                }
            }
        }

        previousPhase = phase
    }

    /**
     * Always (re)submits the standing Dominant/[DOMINANT_SUB_SCORE] bid — the
     * only home tier this entity ever actually shows, per class doc — and,
     * on a genuine phase transition, layers a Special Condition pulse on top
     * (see class doc). A non-transition re-emission (e.g. a re-poll that
     * doesn't change [RidePhase]) only refreshes the Dominant bid, same as
     * Navigation's own-direction-unchanged case.
     */
    private fun submitDominantOrPulse(phase: RidePhase, transitioned: Boolean) {
        val dominantBid = Phs3Priority(
            handler = handler,
            priorityClass = PriorityClass.DOMINANT,
            subScore = DOMINANT_SUB_SCORE,
        )
        service.phs3Manager.scheduler.submit(dominantBid)

        if (!transitioned) return

        Phs3DebugLog.onPoll("Ride", "phase transition -> $phase, Special Condition pulse")
        dwellJob?.cancel()
        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler = handler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore = SPECIAL_CONDITION_SUB_SCORE,
                holdMs = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                fallback = dominantBid,
            )
        )
        service.phs3Manager.surfaceEventDriven(handler)
        dwellJob = scope.launch {
            delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
            service.phs3Manager.resumeAfterEventDriven()
        }
    }

    // ── GPS polling ──────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // caller must already hold ACCESS_FINE_LOCATION
    private fun ensurePolling(intervalMs: Long) {
        if (currentPollIntervalMs == intervalMs && locationCallback != null) return
        stopLocationUpdates()
        currentPollIntervalMs = intervalMs

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                RideRepository.onLocationUpdate(LatLon(loc.latitude, loc.longitude))
            }
        }
        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, null)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        currentPollIntervalMs = -1L
        pollJob?.cancel()
        pollJob = null
    }
}