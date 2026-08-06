package com.example.fiddler.subapps.Fidland.phs3.weather

import android.annotation.SuppressLint
import android.content.Context
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * WeatherPhs3Trigger
 *
 * Always-on trigger — activates [WeatherPhs3Handler] once on start and keeps
 * it active for the lifetime of the service. Weather is always relevant, so
 * there is no qualify/deactivate logic; the handler is registered immediately
 * and only removed when [stop] is called (i.e. service destroy).
 *
 * ── Location strategy ────────────────────────────────────────────────────────
 *   Uses [FusedLocationProviderClient.getCurrentLocation] (same client already
 *   used by RidePhs3Trigger) with PRIORITY_BALANCED_POWER_ACCURACY.
 *   Weather doesn't need precise GPS — cell/wifi accuracy is fine and cheaper
 *   on battery. Location is re-fetched on every refresh cycle so the pill
 *   stays accurate if the user has moved.
 *
 * ── Refresh cadence ──────────────────────────────────────────────────────────
 *   Fetches immediately on start, then every [WeatherRepository.REFRESH_INTERVAL_MS]
 *   (15 minutes). The loop runs for the lifetime of the service.
 *
 * ── Error handling ───────────────────────────────────────────────────────────
 *   If location is unavailable or the network call fails, the handler keeps
 *   showing whatever snapshot [WeatherRepository.flow] last emitted. On first
 *   start with no prior snapshot the handler shows a loading state — see
 *   [WeatherPhs3Handler.Indicator].
 *
 * ── §B7 wiring (this pass) — Weather's scheduler bids ────────────────────────
 * Class: home Dominant, sub-score [HOME_SUB_SCORE] (20 — leaning low, opposite
 * Music). Submitted once in [start] and left standing for the trigger's
 * lifetime — Weather has no qualify/disqualify cycle, so unlike every other
 * Dominant/Submissive home bid in this codebase there's no register/unregister
 * edge to resubmit it on.
 *
 * **Special Condition, sub-score [SPECIAL_CONDITION_SUB_SCORE] (55, same tier
 * as Football)** — extreme weather (rain/thunderstorm/snow, or crossing the
 * new [HIGH_WIND_THRESHOLD_KMH]/[HEATWAVE_THRESHOLD_C] thresholds below) gets
 * [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] on the rotating slot,
 * falling back to the standing home bid via [Phs3Priority.fallback].
 *
 * **First promotion cooldown in the doc:** rate-limited to once per
 * [PROMOTION_COOLDOWN_MS] (30 min) via [lastPromotedAtMs] — every other
 * Special-Condition case elsewhere in this codebase fires on every trigger
 * occurrence, uncapped. Without this, a multi-hour heatwave or storm would
 * re-promote (and re-interrupt rotation) on every 15-minute refresh tick for
 * as long as the condition holds; the cooldown caps that to one interruption
 * per half hour regardless of how long the condition persists. [isExtreme]
 * is still evaluated every tick (so a fresh occurrence right after the
 * cooldown expires promotes immediately) — only the *promotion*, not the
 * detection, is rate-limited.
 *
 * **New threshold constants (this pass, previously undefined):**
 * rain/thunderstorm/snow map directly to existing [WeatherCondition] values
 * — no new constant needed there. High-wind and heatwave are **not**
 * WMO-derived, so they needed picking from scratch:
 *   • [HIGH_WIND_THRESHOLD_KMH] = 50 km/h — Beaufort force 7 ("near gale")
 *     onset; roughly the low end of a US NWS Wind Advisory. Chosen as the
 *     point sustained wind starts being disruptive enough to be worth
 *     interrupting the pill for, without firing on every breezy afternoon.
 *   • [HEATWAVE_THRESHOLD_C] = 35°C (95°F) — a commonly used "extreme heat"
 *     line across multiple national weather services' single-day advisories.
 *     Note this is a single-reading proxy, not a true multi-day heatwave
 *     definition (which needs a rolling baseline this snapshot doesn't
 *     carry) — flagged here as a simplification, not a claim of
 *     meteorological rigor. Revisit either constant if a different figure
 *     gets signed off — same "resolve before building rather than leave
 *     unbuilt" steer §B9 used for Battery's Dominant sub-score.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var weatherTrigger: WeatherPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       weatherTrigger = WeatherPhs3Trigger(applicationContext, serviceScope, this)
 *       weatherTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       weatherTrigger.stop()
 *       ...
 *   }
 */
class WeatherPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {

    private val handler = WeatherPhs3Handler()
    private var refreshJob: Job? = null
    private var dwellJob: Job? = null

    /** Epoch-ms of the last Special-Condition promotion — gates [PROMOTION_COOLDOWN_MS]. Null = never promoted yet this session. */
    private var lastPromotedAtMs: Long? = null

    private val homeBid = Phs3Priority(
        handler       = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore      = HOME_SUB_SCORE,
    )

    // Reuse the same FusedLocationProviderClient that RidePhs3Trigger uses —
    // no need for a separate client instance. Lazy so it's never created if
    // the trigger is constructed but never started.
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Weather")

        // Register the handler immediately — weather is always-on.
        service.activatePhs3(handler)

        // Standing home bid — no register/unregister edge to resubmit it on
        // (see class doc), so it's submitted once here and left alone.
        service.phs3Manager.scheduler.submit(homeBid)

        // Kick off the refresh loop.
        refreshJob = scope.launch {
            while (true) {
                tick()
                delay(WeatherRepository.REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Weather")
        refreshJob?.cancel()
        refreshJob = null
        dwellJob?.cancel()
        dwellJob = null
        service.phs3Manager.scheduler.withdraw(handler.label)
        service.deactivatePhs3(handler.label)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // caller (FidlandService) already holds ACCESS_COARSE_LOCATION
    private suspend fun tick() {
        val location = try {
            fusedClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()
        } catch (e: Exception) {
            Phs3DebugLog.onPoll("Weather", "location unavailable: ${e.message}")
            null
        }

        if (location == null) {
            Phs3DebugLog.onPoll("Weather", "location null — skipping fetch, retaining last snapshot")
            return
        }

        Phs3DebugLog.onPoll(
            "Weather",
            "lat=%.4f lon=%.4f — fetching Open-Meteo".format(location.latitude, location.longitude)
        )

        WeatherRepository.refresh(location.latitude, location.longitude)

        val snap = WeatherRepository.flow.value
        if (snap != null) {
            Phs3DebugLog.onPoll(
                "Weather",
                "${snap.condition.toLabel()} ${snap.tempC}°C feels ${snap.feelsLikeC}°C " +
                        "wind=${snap.windSpeedKmh}km/h extreme=${isExtreme(snap)}"
            )
            maybePromote(snap)
        }
    }

    /** True if [snap] qualifies as extreme weather per the §B7 thresholds — see class doc. */
    private fun isExtreme(snap: WeatherSnapshot): Boolean {
        val extremeCondition = snap.condition == WeatherCondition.RAIN ||
                snap.condition == WeatherCondition.THUNDERSTORM ||
                snap.condition == WeatherCondition.SNOW
        return extremeCondition ||
                snap.windSpeedKmh >= HIGH_WIND_THRESHOLD_KMH ||
                snap.tempC >= HEATWAVE_THRESHOLD_C
    }

    /**
     * Promotes to the Special-Condition/[SPECIAL_CONDITION_SUB_SCORE] bid and
     * grabs the rotating slot for [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS],
     * but only if [snap] is [isExtreme] **and** at least [PROMOTION_COOLDOWN_MS]
     * has elapsed since the last promotion — see class doc's cooldown note.
     */
    private fun maybePromote(snap: WeatherSnapshot) {
        if (!isExtreme(snap)) return

        val now = System.currentTimeMillis()
        val last = lastPromotedAtMs
        if (last != null && now - last < PROMOTION_COOLDOWN_MS) {
            Phs3DebugLog.onPoll(
                "Weather",
                "extreme but cooling down (${(now - last) / 1000}s since last promotion)"
            )
            return
        }
        lastPromotedAtMs = now

        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler       = handler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore      = SPECIAL_CONDITION_SUB_SCORE,
                holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                fallback      = homeBid,
            )
        )

        dwellJob?.cancel()
        service.phs3Manager.surfaceEventDriven(handler)
        dwellJob = scope.launch {
            delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
            service.phs3Manager.resumeAfterEventDriven()
        }
    }

    private companion object {
        /** Home-class sub-score — leaning low, opposite Music. */
        const val HOME_SUB_SCORE = 20

        /** Extreme-weather promotion sub-score — same tier as Football. */
        const val SPECIAL_CONDITION_SUB_SCORE = 55

        /** Rate limit on Special-Condition promotions — see class doc. */
        const val PROMOTION_COOLDOWN_MS = 30 * 60 * 1000L

        /** Beaufort force 7 ("near gale") onset — see class doc. */
        const val HIGH_WIND_THRESHOLD_KMH = 50

        /** Common single-day "extreme heat" line — see class doc. */
        const val HEATWAVE_THRESHOLD_C = 35
    }
}