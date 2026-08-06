package com.example.fiddler.subapps.Fidland.phs3.battery

/**
 * Phs3 module — Battery indicator — shared state.
 *
 * Populated from [android.content.Intent.ACTION_BATTERY_CHANGED] (sticky
 * broadcast — always has a value immediately, no need to wait for a change).
 * See [BatteryPhs3Trigger] for the receiver wiring, rate estimation, and
 * charge-cycle accounting that populate the fields below.
 *
 * ── Display trigger (resolved) ──────────────────────────────────────────────
 * The handoff doc left this open: "always-on chip vs. only on low battery /
 * while charging." Going with the latter — an always-on chip would occupy
 * the pill's right zone permanently for a state that's true 100% of the
 * time, which defeats the point of a *compact* indicator. Instead this
 * qualifies (see [isActive]) only when the information is actually
 * noteworthy:
 *
 *   • Charging — transient, useful to confirm the cable/pad is actually
 *     delivering power.
 *   • Low battery while discharging — the one battery state that's
 *     genuinely urgent.
 *
 * Normal-range, non-charging battery is the common case and stays silent,
 * same as Camera/Flashlight staying silent when their sensor is idle.
 * §B7/Phase 3: [isActive]'s formula is unchanged by this pass — the new
 * conditional-Dominant/Special-Condition/hard-override logic below is all
 * layered on top of the same base qualify signal, not a new one.
 *
 * @param level      Battery level, 0..100.
 * @param isCharging True while USB/AC/wireless charging is connected and
 *                   the system reports a charging (or full-while-plugged)
 *                   status.
 * @param chargeCycleCount Persisted count of completed charge cycles (100
 *                   cumulative percentage points charged, the conventional
 *                   battery-industry definition — not "number of times
 *                   plugged in"). See [BatteryPhs3Trigger] for the
 *                   SharedPreferences-backed accumulator.
 * @param minutesToFull Rate-based estimate of minutes until
 *                   [DOMINANT_ESCALATION_TO_FULL_PERCENT]% while charging,
 *                   or null while discharging or before enough samples
 *                   exist to trust a rate. §B7 calls this "time-to-85%-full."
 * @param minutesToEmpty Rate-based estimate of minutes until 0% while
 *                   discharging, or null while charging or before enough
 *                   samples exist. §B7 calls this "hours-to-0%" — stored in
 *                   minutes here for estimate precision, formatted as
 *                   hours+minutes at display time.
 * @param avgDailyScreenOnMinutes Approximate minutes of device usage over
 *                   the last 24h via `UsageStatsManager`, or null if the
 *                   special "usage access" permission hasn't been granted.
 *                   See [BatteryPhs3Trigger]'s class doc for the
 *                   approximation this uses (summed foreground app time,
 *                   not a literal screen-on/off scan) and why.
 */
data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val chargeCycleCount: Int = 0,
    val minutesToFull: Int? = null,
    val minutesToEmpty: Int? = null,
    val avgDailyScreenOnMinutes: Int? = null,
) {
    /** Whether the phs3 slot should be showing at all — see class kdoc. Unchanged this pass. */
    val isActive: Boolean
        get() = isCharging || (level <= LOW_BATTERY_THRESHOLD_PERCENT)

    /**
     * §B7's "conditional home class" seed example: escalates the home bid
     * from Submissive/15 to Dominant/[BATTERY_DOMINANT_SUB_SCORE] while
     * discharging below [DOMINANT_ESCALATION_THRESHOLD_PERCENT]%. Charging
     * never escalates here regardless of level — a charging phone isn't
     * urgent the way a draining one below 15% is.
     */
    val isDominant: Boolean
        get() = !isCharging && level <= DOMINANT_ESCALATION_THRESHOLD_PERCENT

    /**
     * §B7's <5%-critical case: Special Condition, held indefinitely, hard
     * override — beats every other entity outright regardless of class.
     * Charging always clears this even below 5%, since a charging-but-
     * still-critical phone is recovering, not in the state the override
     * exists to flag.
     */
    val isCritical: Boolean
        get() = !isCharging && level <= CRITICAL_BATTERY_THRESHOLD_PERCENT
}

/** Returned before the first ACTION_BATTERY_CHANGED broadcast arrives. */
val EmptyBatteryInfo = BatteryInfo()

/**
 * Battery level, at or below which the indicator qualifies even while
 * discharging (not charging). 20% matches Android's own system low-battery
 * warning threshold, so the pill lines up with what the user already
 * expects to be "low."
 */
const val LOW_BATTERY_THRESHOLD_PERCENT: Int = 20

/**
 * §B7: below this level while discharging, the home bid conditionally
 * escalates from Submissive/15 to Dominant/[BATTERY_DOMINANT_SUB_SCORE].
 */
const val DOMINANT_ESCALATION_THRESHOLD_PERCENT: Int = 15

/** §B7: proposed default (§B8 #8, not yet separately confirmed) for the conditional-Dominant escalation. Adopted per the §B9 Phase 3 roadmap note ("resolve the trivial missing sub-score... then build") rather than left unbuilt — revisit if a different figure gets signed off. */
const val BATTERY_DOMINANT_SUB_SCORE: Int = 40

/** §B7: threshold-crossing Special-Condition sub-score, confirmed. */
const val BATTERY_THRESHOLD_CROSSING_SUB_SCORE: Int = 60

/** §B7: level at/below which discharging becomes the indefinite hard-override case. */
const val CRITICAL_BATTERY_THRESHOLD_PERCENT: Int = 5

/** §B7: the "time-to-85%-full" target level used for [BatteryInfo.minutesToFull]. */
const val TIME_TO_FULL_TARGET_PERCENT: Int = 85

/**
 * §B7 discharging threshold-crossing set, ascending (crossing detection
 * walks level *down* through these as it drains).
 */
val DISCHARGING_SPECIAL_CONDITION_THRESHOLDS: List<Int> =
    listOf(10, 15, 20, 30, 40, 50, 60, 70, 80)

/**
 * §B7 charging threshold-crossing set, ascending (crossing detection walks
 * level *up* through these while charging). ⚠ Doc flags an open question:
 * confirm whether 20% is intentionally the first charging threshold, or
 * should start lower — kept as documented pending that confirmation.
 */
val CHARGING_SPECIAL_CONDITION_THRESHOLDS: List<Int> =
    listOf(20, 40, 60, 80, 85, 100)