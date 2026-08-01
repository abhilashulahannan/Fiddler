package com.example.fiddler.subapps.Fidland.phs3.battery

/**
 * Phs3 module — Battery indicator — shared state.
 *
 * Populated from [android.content.Intent.ACTION_BATTERY_CHANGED] (sticky
 * broadcast — always has a value immediately, no need to wait for a change).
 * See [BatteryPhs3Trigger] for the receiver wiring.
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
 *
 * @param level      Battery level, 0..100.
 * @param isCharging True while USB/AC/wireless charging is connected and
 *                   the system reports a charging (or full-while-plugged)
 *                   status.
 */
data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
) {
    /** Whether the phs3 slot should be showing at all — see class kdoc. */
    val isActive: Boolean
        get() = isCharging || (level <= LOW_BATTERY_THRESHOLD_PERCENT)
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