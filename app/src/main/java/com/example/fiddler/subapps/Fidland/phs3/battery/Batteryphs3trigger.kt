package com.example.fiddler.subapps.Fidland.phs3.battery

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.phs3.Phs3RotationPartitioner
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BatteryPhs3Trigger
 *
 * Registers a receiver for [Intent.ACTION_BATTERY_CHANGED] and activates /
 * deactivates the Battery phs3 slot as the level and charging state change.
 * Same state/trigger/handler split as [com.example.fiddler.subapps.Fidland.phs3.camera.CameraPhs3Trigger].
 *
 * ── Why ACTION_BATTERY_CHANGED, not a periodic poll ───────────────────────────
 * [Intent.ACTION_BATTERY_CHANGED] is a *sticky* broadcast — registering for
 * it returns the current battery state immediately (no need to wait for the
 * next change), and the system re-delivers it on every subsequent level or
 * plug-state change without any polling loop or wakelock on our part.
 *
 * ── §B9 Phase 3 build (this pass) — full §B7 spec ────────────────────────────
 * Phase 1 proved the EVENT_DRIVEN + [Phs3Scheduler] mechanism with a flat
 * home-Submissive/15 bid and a placeholder dwell. This pass builds the rest
 * of §B7:
 *
 * • **Conditional home class** — home Submissive/15, escalating to
 *   Dominant/[BATTERY_DOMINANT_SUB_SCORE] (proposed default, §B8 #8 —
 *   adopted per the Phase 3 roadmap note rather than left unbuilt) while
 *   discharging at or below [DOMINANT_ESCALATION_THRESHOLD_PERCENT]%. See
 *   [BatteryInfo.isDominant].
 * • **Threshold-crossing Special Condition** — sub-score
 *   [BATTERY_THRESHOLD_CROSSING_SUB_SCORE] (60, confirmed) for
 *   [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS], auto-reverting to
 *   the current home bid via [Phs3Priority.fallback]. Fires on crossing any
 *   [DISCHARGING_SPECIAL_CONDITION_THRESHOLDS] / [CHARGING_SPECIAL_CONDITION_THRESHOLDS]
 *   value (see [crossedThreshold]), **or** on the charger-connect edge
 *   (not-charging→charging) even when that edge doesn't itself cross a
 *   listed percentage — its own edge-detection per §B7, since
 *   ACTION_BATTERY_CHANGED keeps re-firing while connected and only the
 *   transition itself is "the event."
 * • **<5% critical** — [BatteryInfo.isCritical] submits a hard-override
 *   ([Phs3Priority.isHardOverride]) Special-Condition bid held indefinitely
 *   (`holdMs = null`) — beats every other entity outright, any class,
 *   until the level recovers or charging starts.
 * • **New data sources** — [chargeCycleCount] (SharedPreferences-backed
 *   100-cumulative-percent accumulator, the conventional definition), a
 *   simple rate estimator over recent (timestamp, level) samples for
 *   [minutesToFull]/[minutesToEmpty], and an approximate avg-daily-
 *   screen-on figure via `UsageStatsManager` (see [queryAvgDailyScreenOnMinutes]
 *   for the approximation this uses and why, and [hasUsageAccess] for the
 *   special-permission gating — degrades to `null`/hidden rather than
 *   auto-prompting the user the way Ring Mode's DND grant flow does, since
 *   Battery registers unprompted at service start rather than in response
 *   to a user action).
 *
 * ── Display-slot vs. priority-bid — two separate mechanisms ──────────────────
 * [Phs3Manager.scheduler]'s bid (submitted here) decides *who's winning* by
 * class/sub-score, and reverts itself automatically on [Phs3Priority.holdMs]
 * expiry via [Phs3Priority.fallback] — see [Phs3Scheduler]'s own class doc.
 * That is independent of *actually holding the rotating right-zone slot*,
 * which is [surfaceEventDriven]/[resumeAfterEventDriven]'s job (via
 * [dwellJob] here) — this trigger still has to manage that half itself,
 * same as Phase 1, just with a dwell length that now matches whichever bid
 * just won (placeholder [Phs3RotationPartitioner.ROTATION_INTERVAL_MS] for
 * a plain home take, [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] for
 * a threshold-crossing promotion, no dwell/resume at all for the critical
 * hard-override).
 *
 * ── Permissions ────────────────────────────────────────────────────────────
 * None required for the core battery signal — [Intent.ACTION_BATTERY_CHANGED]
 * is a normal sticky broadcast, same tier as the other phs3 broadcast
 * receivers (Ringmode). [avgDailyScreenOnMinutes] additionally needs the
 * special "usage access" app-op (`PACKAGE_USAGE_STATS`), granted manually
 * by the user in system settings — not a runtime-requestable permission.
 * Not requested automatically here; see [hasUsageAccess].
 *
 * ── Wire-up in FidlandService ──────────────────────────────────────────────
 *
 *   private lateinit var batteryTrigger: BatteryPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       batteryTrigger = BatteryPhs3Trigger(applicationContext, serviceScope, this)
 *       batteryTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       batteryTrigger.stop()
 *       ...
 *   }
 *
 * ── Debugging ──────────────────────────────────────────────────────────────
 * Logs to Phs3DebugLog (visible in the Debugging screen): trigger
 * start/stop, and one POLL entry per battery broadcast showing level,
 * charging state, and whether the slot is active.
 */
class BatteryPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    // Stateless placeholder — just used for the label on deactivation /
    // scheduler withdrawal; the real handler pushed on every active
    // broadcast carries the live [BatteryInfo] snapshot.
    private val handler = BatteryPhs3Handler()

    /** True once the current activation has already taken the rotating slot — see class doc's "display-slot vs. priority-bid" note. */
    private var isSurfacing = false
    private var dwellJob: Job? = null

    /** True while the <5% hard-override is the live bid — used to detect when it clears (level recovers, or charging starts). */
    private var isCriticalOverride = false

    private var prevLevel: Int? = null
    private var prevIsCharging: Boolean = false

    // ── §B7 rate estimation (time-to-full / time-to-empty) ──────────────────

    /** (timestampMs, level) samples, same charge-direction only — cleared whenever the direction flips. See [estimateMinutes]. */
    private val rateSamples = ArrayDeque<Pair<Long, Int>>()

    // ── §B7 charge-cycle accounting ──────────────────────────────────────────

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // ── §B7 avg-daily-screen-on (cached — queried at most every REFRESH interval, not on every broadcast) ──

    private var cachedAvgDailyScreenOnMinutes: Int? = null
    private var lastScreenOnQueryAtMs: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            publish(intent)
        }
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Battery")
        // ACTION_BATTERY_CHANGED is sticky — context.registerReceiver(receiver, filter)
        // returns the current battery intent immediately, so no separate
        // "read initial state" call is needed before start() returns.
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Battery")
        try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        dwellJob?.cancel()
        dwellJob = null
        isSurfacing = false
        isCriticalOverride = false
        service.phs3Manager.scheduler.withdraw(handler.label)
        service.deactivatePhs3(handler.label)
    }

    // ── Internal — publish ───────────────────────────────────────────────────

    private fun publish(intent: Intent) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val level = if (rawLevel >= 0 && rawScale > 0) {
            (rawLevel * 100) / rawScale
        } else {
            100
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)

        updateRateSamples(level, isCharging)
        recordChargeSample(level, isCharging)
        refreshScreenOnEstimateIfDue()

        val info = BatteryInfo(
            level = level,
            isCharging = isCharging,
            chargeCycleCount = prefs.getInt(KEY_CHARGE_CYCLES, 0),
            minutesToFull = estimateMinutesToFull(level, isCharging),
            minutesToEmpty = estimateMinutesToEmpty(level, isCharging),
            avgDailyScreenOnMinutes = cachedAvgDailyScreenOnMinutes,
        )
        Phs3DebugLog.onPoll("Battery", "level=$level charging=$isCharging active=${info.isActive} dominant=${info.isDominant} critical=${info.isCritical}")

        if (info.isActive) {
            val freshHandler = BatteryPhs3Handler(batteryInfo = info)
            service.activatePhs3(freshHandler) // keeps the icon/text live regardless of slot ownership

            val homeBid = homePriority(freshHandler, info)
            val isFirstSample = prevLevel == null
            val justPluggedIn = !isFirstSample && isCharging && !prevIsCharging
            val crossed = !isFirstSample && (prevLevel?.let { crossedThreshold(it, level, isCharging) } ?: false)

            when {
                info.isCritical -> submitCritical(freshHandler)
                else -> {
                    if (isCriticalOverride) {
                        // Recovered from <5% (level rose, or charging started) — hard
                        // override clears; fall through to normal handling below.
                        isCriticalOverride = false
                    }
                    if (crossed || justPluggedIn) {
                        submitThresholdCrossing(freshHandler, homeBid)
                    } else {
                        submitHome(freshHandler, homeBid)
                    }
                }
            }
        } else {
            dwellJob?.cancel()
            dwellJob = null
            isSurfacing = false
            isCriticalOverride = false
            service.phs3Manager.scheduler.withdraw(handler.label)
            service.deactivatePhs3(handler.label) // triggers the partitioner's own interrupt fallback if mid-dwell — see BatteryPhs3Trigger's Phase-1 note
        }

        prevLevel = level
        prevIsCharging = isCharging
    }

    // ── Internal — priority submission ───────────────────────────────────────

    private fun homePriority(freshHandler: BatteryPhs3Handler, info: BatteryInfo): Phs3Priority =
        if (info.isDominant) {
            Phs3Priority(freshHandler, PriorityClass.DOMINANT, BATTERY_DOMINANT_SUB_SCORE)
        } else {
            Phs3Priority(freshHandler, PriorityClass.SUBMISSIVE, 15)
        }

    /** <5% discharging — hard override, indefinite. Re-submitted on every broadcast while still critical so the bid always points at the latest handler instance. */
    private fun submitCritical(freshHandler: BatteryPhs3Handler) {
        dwellJob?.cancel()
        dwellJob = null
        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler = freshHandler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore = 100,
                isHardOverride = true,
                holdMs = null,
            )
        )
        if (!isCriticalOverride || !isSurfacing) {
            isCriticalOverride = true
            isSurfacing = true
            service.phs3Manager.surfaceEventDriven(freshHandler)
        }
    }

    /** Threshold crossed, or charger-connect edge — Special Condition, standard dwell, reverts to [homeBid] automatically via the scheduler's own fallback machinery. */
    private fun submitThresholdCrossing(freshHandler: BatteryPhs3Handler, homeBid: Phs3Priority) {
        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler = freshHandler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore = BATTERY_THRESHOLD_CROSSING_SUB_SCORE,
                holdMs = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                fallback = homeBid,
            )
        )
        dwellJob?.cancel()
        isSurfacing = true
        service.phs3Manager.surfaceEventDriven(freshHandler)
        dwellJob = scope.launch {
            delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
            isSurfacing = false
            service.phs3Manager.resumeAfterEventDriven()
        }
    }

    /** No promotion this tick — plain home bid, same one-turn placeholder dwell as Phase 1's mechanism proof. */
    private fun submitHome(freshHandler: BatteryPhs3Handler, homeBid: Phs3Priority) {
        service.phs3Manager.scheduler.submit(homeBid)
        if (!isSurfacing) {
            isSurfacing = true
            service.phs3Manager.surfaceEventDriven(freshHandler)
            dwellJob?.cancel()
            dwellJob = scope.launch {
                delay(Phs3RotationPartitioner.ROTATION_INTERVAL_MS)
                isSurfacing = false
                service.phs3Manager.resumeAfterEventDriven()
            }
        }
    }

    // ── Internal — threshold crossing ────────────────────────────────────────

    /** True if [newLevel] crossed (in the direction implied by [isCharging]) any §B7 threshold since [prevLevelValue]. */
    private fun crossedThreshold(prevLevelValue: Int, newLevel: Int, isCharging: Boolean): Boolean {
        val thresholds = if (isCharging) CHARGING_SPECIAL_CONDITION_THRESHOLDS else DISCHARGING_SPECIAL_CONDITION_THRESHOLDS
        return thresholds.any { t ->
            if (isCharging) newLevel >= t && prevLevelValue < t
            else newLevel <= t && prevLevelValue > t
        }
    }

    // ── Internal — rate estimation ───────────────────────────────────────────

    private fun updateRateSamples(level: Int, isCharging: Boolean) {
        // A rate computed across a charging↔discharging flip would be
        // meaningless — reset the window whenever the direction changes.
        if (rateSamples.isNotEmpty() && rateDirectionWasCharging != isCharging) {
            rateSamples.clear()
        }
        rateDirectionWasCharging = isCharging
        val now = System.currentTimeMillis()
        rateSamples.addLast(now to level)
        val cutoff = now - RATE_WINDOW_MS
        while (rateSamples.isNotEmpty() && rateSamples.first().first < cutoff) {
            rateSamples.removeFirst()
        }
    }

    private var rateDirectionWasCharging: Boolean = false

    private fun estimateMinutesToFull(level: Int, isCharging: Boolean): Int? {
        if (!isCharging || level >= TIME_TO_FULL_TARGET_PERCENT) return null
        return estimateMinutes(targetLevel = TIME_TO_FULL_TARGET_PERCENT, rising = true)
    }

    private fun estimateMinutesToEmpty(level: Int, isCharging: Boolean): Int? {
        if (isCharging || level <= 0) return null
        return estimateMinutes(targetLevel = 0, rising = false)
    }

    /** Simple linear rate over the current [rateSamples] window — no smoothing beyond the window itself, since broadcasts already arrive in coarse 1% steps. */
    private fun estimateMinutes(targetLevel: Int, rising: Boolean): Int? {
        if (rateSamples.size < 2) return null
        val (t0, l0) = rateSamples.first()
        val (t1, l1) = rateSamples.last()
        val elapsedMin = (t1 - t0) / 60_000f
        if (elapsedMin < MIN_SAMPLE_WINDOW_MINUTES) return null // not enough signal yet to trust a slope
        val deltaLevel = (l1 - l0).toFloat()
        if ((rising && deltaLevel <= 0f) || (!rising && deltaLevel >= 0f)) return null // rate isn't moving the expected direction (e.g. temperature-throttled charging)
        val ratePerMin = deltaLevel / elapsedMin
        val remaining = (targetLevel - l1).toFloat()
        val minutes = remaining / ratePerMin
        return minutes.takeIf { it.isFinite() && it > 0f }?.toInt()?.coerceAtMost(24 * 60)
    }

    // ── Internal — charge-cycle accounting ───────────────────────────────────

    /**
     * Conventional battery-industry definition: one cycle = 100 cumulative
     * percentage points charged (not "plugged in N times" — a phone
     * topped off from 80% to 100% five times is one cycle, not five).
     * Persisted so the count survives process death / reboot.
     */
    private fun recordChargeSample(level: Int, isCharging: Boolean) {
        val editor = prefs.edit()
        if (isCharging) {
            val lastLevel = prefs.getInt(KEY_LAST_CHARGING_LEVEL, level)
            if (level > lastLevel) {
                val delta = (level - lastLevel).toFloat()
                var cumulative = prefs.getFloat(KEY_CUMULATIVE_CHARGED, 0f) + delta
                var cycles = prefs.getInt(KEY_CHARGE_CYCLES, 0)
                while (cumulative >= 100f) {
                    cycles += 1
                    cumulative -= 100f
                }
                editor.putFloat(KEY_CUMULATIVE_CHARGED, cumulative)
                editor.putInt(KEY_CHARGE_CYCLES, cycles)
            }
            editor.putInt(KEY_LAST_CHARGING_LEVEL, level)
        } else {
            editor.remove(KEY_LAST_CHARGING_LEVEL)
        }
        editor.apply()
    }

    // ── Internal — avg daily screen-on (approximate) ─────────────────────────

    /**
     * Refreshes [cachedAvgDailyScreenOnMinutes] at most once every
     * [SCREEN_ON_REFRESH_INTERVAL_MS] — `UsageStatsManager` queries aren't
     * free, and this figure doesn't need broadcast-level freshness.
     */
    private fun refreshScreenOnEstimateIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastScreenOnQueryAtMs < SCREEN_ON_REFRESH_INTERVAL_MS) return
        lastScreenOnQueryAtMs = now
        cachedAvgDailyScreenOnMinutes = queryAvgDailyScreenOnMinutes()
    }

    /**
     * ⚠ Approximation, not a literal screen-on/off scan: sums each app's
     * foreground time over the last 24h via
     * [UsageStatsManager.queryAndAggregateUsageStats]. `UsageStatsManager`
     * doesn't expose true screen-interactive state without walking raw
     * `UsageEvents` for `SCREEN_INTERACTIVE`/`SCREEN_NON_INTERACTIVE`
     * pairs — flagged here rather than silently treated as exact; swap in
     * an events-based scan if §B7 needs literal screen state instead of
     * this "time something was in the foreground" proxy.
     */
    private fun queryAvgDailyScreenOnMinutes(): Int? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val dayStart = now - 24L * 60 * 60 * 1000
        val stats = try {
            usm.queryAndAggregateUsageStats(dayStart, now)
        } catch (_: Exception) {
            return null
        }
        val totalForegroundMs = stats.values.sumOf { it.totalTimeInForeground }
        return (totalForegroundMs / 60_000L).toInt().coerceIn(0, 24 * 60)
    }

    /** Special app-op, not a runtime-requestable permission — user grants it once via system Settings. Not auto-prompted here; see class doc. */
    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private companion object {
        const val PREFS_NAME = "battery_phs3"
        const val KEY_CHARGE_CYCLES = "charge_cycles"
        const val KEY_CUMULATIVE_CHARGED = "cumulative_charged_percent"
        const val KEY_LAST_CHARGING_LEVEL = "last_charging_level"

        /** Rolling window for the rate estimator — long enough to smooth coarse 1% broadcast steps, short enough to react to a charger unplug/replug. */
        const val RATE_WINDOW_MS = 20 * 60 * 1000L
        /** Minimum span of data before a rate estimate is trusted — avoids a wild estimate off two samples a few seconds apart. */
        const val MIN_SAMPLE_WINDOW_MINUTES = 3f
        const val SCREEN_ON_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    }
}