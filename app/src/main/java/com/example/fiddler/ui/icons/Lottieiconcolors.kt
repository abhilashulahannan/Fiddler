package com.example.fiddler.ui.icons

import androidx.annotation.RawRes
import androidx.compose.ui.graphics.Color
import com.example.fiddler.R

/**
 * Single source of truth for how each black Lottie icon gets recoloured.
 *
 * Every animation in res/raw ships pure black. [MonoLottieIcon] stencils a
 * solid colour over whatever the animation draws (see that file for the
 * mechanics) — this object just says *which* colour, per icon.
 *
 * ── How to use ────────────────────────────────────────────────────────────
 * Tweak a value below and every call site using that icon updates — nobody
 * hardcodes a tint next to a `MonoLottieIcon(rawRes = ...)` call unless the
 * colour is state-driven (see "Dynamic exceptions" at the bottom).
 *
 * To add a new icon: drop the raw asset in res/raw, add a `val` here, add it
 * to [map]. [forRawRes] falls back to [white] for anything not listed, so a
 * missing entry never crashes — it's just visibly wrong (all-white), which
 * is your cue to add it.
 */
object LottieIconColors {

    // ── Base palette — change these and every icon that references them
    //    moves together. Individual icons below are free to use a one-off
    //    Color(0xFF......) instead if they shouldn't move with the palette. ──
    private val white  = Color(0xFFFFFFFF)
    private val grey   = Color(0xFF9A9A9A)
    private val green  = Color(0xFF4ADE80)
    private val blue   = Color(0xFF60A5FA)
    private val red    = Color(0xFFFC5C5C)
    private val purple = Color(0xFFA78BFA)
    private val amber  = Color(0xFFFFCC00)
    private val yellow = Color(0xFFEAB308)

    // ── Alarm ─────────────────────────────────────────────────────────────
    /** Resting/default tint. AlarmClockIcon overrides this per urgency
     *  stage (green → yellow → red as the alarm approaches) — see the
     *  "Dynamic exceptions" note below. */
    val alarm = green

    // ── Battery ───────────────────────────────────────────────────────────
    // No longer used — BatteryPhs3Handler now renders a hand-drawn Canvas
    // icon (BatteryIcon.kt) instead of these two static Lottie loops, so its
    // colour is computed directly from level/isCharging rather than looked
    // up here. Left as dead entries below only because the res/raw assets
    // still ship; safe to delete once the assets themselves are removed.
    val batteryCharge = green
    val batteryNormal = amber // shown only while discharging-and-low

    // ── Call ──────────────────────────────────────────────────────────────
    val callActive = green
    val callMissed = red

    // ── Calendar ──────────────────────────────────────────────────────────
    val callender = white

    // ── Camera (privacy indicator) ───────────────────────────────────────
    val camera = red

    // ── Comms ─────────────────────────────────────────────────────────────
    val commsBluetooth = white
    val commsWifi24g   = white
    val commsWifi5g     = white
    val commsNfc        = white
    val commsAirplane   = white
    val comms2g = grey
    val comms3g = grey
    val comms4g = white
    val comms5g = white

    // ── Delivery ──────────────────────────────────────────────────────────
    val delivery = blue

    // ── Flashlight ────────────────────────────────────────────────────────
    val flashlight = amber

    // ── Football ──────────────────────────────────────────────────────────
    val footballGoal       = green
    /** Default tint for the combined card asset. Yellow vs red card is
     *  picked at the call site (see [footballCardYellow] / [footballCardRed])
     *  since one asset covers both. */
    val footballCard       = yellow
    val footballCardYellow = yellow
    val footballCardRed    = red
    val footballStadium    = grey

    // ── Idle thoughts ─────────────────────────────────────────────────────
    val idleLama = grey

    // ── Navigation ────────────────────────────────────────────────────────
    val navDirection = white // shared by all nav_*.json turn-arrow assets

    // ── Ring mode ─────────────────────────────────────────────────────────
    val ringRing    = green
    val ringVibrate = blue
    val ringSilent  = red
    val ringDnd     = purple

    // ── Recording (asset-based, not res/raw — see MonoLottieIcon's `spec`
    //    overload). Kept here so it still lives with the rest of the config. ──
    val recordingActive = red
    val recordingPaused = Color(0xFF884444)

    // ── Timer / stopwatch ─────────────────────────────────────────────────
    val timerMode = white

    // ── Weather ───────────────────────────────────────────────────────────
    // Each condition gets its own tint (was one flat `white` for all of
    // them) so the STATE5 hero icon reads at a glance instead of every
    // condition looking identical. Detail icons (temp/humidity/wind) stay
    // neutral since they're supporting stats, not the headline.
    val weatherClear        = amber                // ☀️ sun
    val weatherPartlyCloudy = Color(0xFFB8C4D9)     // soft blue-grey — sun behind cloud
    val weatherOvercast     = grey                  // flat grey sky
    val weatherFog          = Color(0xFFAEB4BA)     // hazy light grey
    val weatherDrizzle      = blue                  // light blue
    val weatherRain         = Color(0xFF3B82F6)     // deeper blue than drizzle
    val weatherThunderstorm = purple
    val weatherSnow         = Color(0xFFCFEFFF)     // pale icy blue
    val weatherTemp         = white                 // hero fallback + "feels like" chip
    val weatherHumidity     = blue
    val weatherWind         = Color(0xFFCFCFCF)     // light neutral grey

    // ── Quick Settings tiles ───────────────────────────────────────────────
    // These drive the on/off tinting in QuickSettingsTopic.
    // "On" state uses the named colour below; "off" state passes [grey]
    // explicitly at the call site rather than looking up a separate entry —
    // keeping the off-state consistent across all tiles without needing a
    // paired _off val for each one.
    //
    // Wi-Fi and Bluetooth reuse the existing commsWifi*/commsBluetooth
    // values above (same asset, same semantic colour) — no new entry needed.
    // NFC likewise reuses commsNfc.
    /** Mobile Data "on" tint — reuses comms4g/5g asset, cyan to read as
     *  "data active" rather than just "cellular generation info". */
    val quickSettingsMobileData = Color(0xFF22D3EE)   // cyan
    /** Torch "on" tint — amber, matching the existing [flashlight] entry.
     *  Extracted here as a named alias so QuickSettingsTopic can reference
     *  it semantically without knowing the amber literal. */
    val quickSettingsTorch      = amber
    /** Dev Options "on" tint — purple signals "developer / advanced". */
    val quickSettingsDevOptions = purple
    /** Accessibility "on" tint — green signals "active / enabled". */
    val quickSettingsAccessibility = green

    /** Maps every res/raw Lottie icon to its default colour above. */
    private val map: Map<Int, Color> by lazy {
        mapOf(
            R.raw.alarm to alarm,

            R.raw.battery_charge to batteryCharge,
            R.raw.battery_normal to batteryNormal,

            R.raw.call_call to callActive,
            R.raw.call_missed to callMissed,

            R.raw.callender to callender,

            R.raw.camera to camera,

            R.raw.comms_bt to commsBluetooth,
            R.raw.comms_wifi24g to commsWifi24g,
            R.raw.comms_wifi5g to commsWifi5g,
            R.raw.comms_nfc to commsNfc,
            R.raw.comms_airplane to commsAirplane,
            R.raw.comms_2g to comms2g,
            R.raw.comms_3g to comms3g,
            R.raw.comms_4g to comms4g,
            R.raw.comms_5g to comms5g,

            R.raw.delivery to delivery,

            R.raw.flashlight to flashlight,

            R.raw.football_gooal to footballGoal,
            R.raw.football_card_ry to footballCard,
            R.raw.football_stadium to footballStadium,

            R.raw.nhi_lama to idleLama,

            R.raw.nav_left to navDirection,
            R.raw.nav_right to navDirection,
            R.raw.nav_straight to navDirection,
            R.raw.nav_mildleft to navDirection,
            R.raw.nav_mildright to navDirection,
            R.raw.nav_sharpleft to navDirection,
            R.raw.nav_sharpright to navDirection,
            R.raw.nav_uturn to navDirection,

            R.raw.ring_ring to ringRing,
            R.raw.ring_vibrate to ringVibrate,
            R.raw.ring_silent to ringSilent,
            R.raw.ring_dnd to ringDnd,

            R.raw.timer_countdown_stopwatch to timerMode,

            R.raw.weather_clear to weatherClear,
            R.raw.weather_partlycloud to weatherPartlyCloudy,
            R.raw.weather_overcast to weatherOvercast,
            R.raw.weather_fog to weatherFog,
            R.raw.weather_drizzle to weatherDrizzle,
            R.raw.weather_rain to weatherRain,
            R.raw.weather_thunderstorm to weatherThunderstorm,
            R.raw.weather_snow to weatherSnow,
            R.raw.weather_temp to weatherTemp,
            R.raw.weather_humid to weatherHumidity,
            R.raw.weather_wind to weatherWind,
        )
    }

    /**
     * Looks up the default colour for [rawRes]. Falls back to [white] if
     * the icon hasn't been added to [map] yet.
     */
    fun forRawRes(@RawRes rawRes: Int): Color = map[rawRes] ?: white

    // ── Dynamic exceptions ───────────────────────────────────────────────
    // A few icons already have state-driven colour logic that lives outside
    // this file — they call MonoLottieIcon with an explicit `color =`
    // argument instead of relying on [forRawRes]:
    //   • AlarmClockIcon    — green/yellow/red urgency ramp (Alarmclockicon.kt)
    //   • FootballEventIcon — yellow vs red card (footballCardYellow/Red above)
    //   • RecordPhs3Handler — recordingActive/recordingPaused above
    //   • QuickSettingsTopic — on/off tinting per tile (quickSettings* above)
    // This is intentional: those colours depend on runtime state, not just
    // "which icon is this", so they can't be a static map entry.
}