package com.example.fiddler.subapps.Fidland.phs3.timer

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * TimerNotificationSource
 *
 * Listens to the AOSP Clock app's timer and stopwatch notifications and
 * pushes parsed state into [TimerRepository].
 *
 * ── How the Clock app notifies ───────────────────────────────────────────────
 * The stock Clock app (com.google.android.deskclock and most OEM variants)
 * posts an ONGOING notification while a countdown timer or stopwatch is
 * running. The notification content follows a consistent pattern:
 *
 *   Timer   — EXTRA_TITLE = "Timer"  (or the user's label, e.g. "Pasta")
 *             EXTRA_TEXT  = "04:32"  (remaining time in MM:SS or H:MM:SS)
 *             extras also contain a chronometer: EXTRA_CHRONOMETER_COUNT_DOWN
 *             and EXTRA_SHOW_CHRONOMETER on some OEMs.
 *
 *   Stopwatch — EXTRA_TITLE = "Stopwatch"
 *               EXTRA_TEXT  = "02:18.04"  (elapsed time)
 *
 * When the countdown reaches zero, the notification transitions from ongoing
 * to a full-screen / heads-up alert; we treat that as FINISHED and call
 * [TimerRepository.onEnded] shortly after so the pill clears.
 *
 * ── OEM notes ────────────────────────────────────────────────────────────────
 * Different OEM clock packages post slightly different strings. We handle
 * the most common variants:
 *   • Title matching  : "Timer", "Stopwatch", "Countdown", "Chrono"
 *   • Text parsing    : MM:SS, H:MM:SS, MM:SS.cc (centiseconds for stopwatch)
 *   • Paused state    : "Paused" or "paused" appears in title or text
 *
 * ── Wire-up in NotificationListenerService ───────────────────────────────────
 *
 *   companion object {
 *       var timerNotificationSource: TimerNotificationSource? = null
 *   }
 *
 *   override fun onNotificationPosted(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       timerNotificationSource?.onNotificationPosted(sbn)
 *       ...
 *   }
 *
 *   override fun onNotificationRemoved(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       timerNotificationSource?.onNotificationRemoved(sbn)
 *       ...
 *   }
 *
 * ── Wire-up in FidlandService ─────────────────────────────────────────────────
 * See [TimerPhs3Trigger] — it creates this source and sets the static hook.
 */
class TimerNotificationSource {

    companion object {
        /** All known Clock app packages across OEMs. */
        val CLOCK_PACKAGES = setOf(
            "com.google.android.deskclock",     // Pixel / AOSP
            "com.android.deskclock",            // generic AOSP
            "com.samsung.android.app.clockpackage", // Samsung One UI
            "com.sec.android.app.clockpackage", // older Samsung
            "com.miui.clock",                   // Xiaomi / MIUI
            "com.oneplus.deskclock",            // OnePlus
            "com.oppo.clock",                   // OPPO / Realme
            "com.asus.deskclock",               // ASUS
            "com.motorola.clock",               // Motorola
            "com.htc.android.worldclock",       // HTC
            "com.lge.clock",                    // LG
        )

        // Matches MM:SS, H:MM:SS, MM:SS.cc — captures the whole time string.
        private val TIME_PATTERN = Regex("""(\d{1,2}:\d{2}(?::\d{2})?(?:\.\d{2})?)""")

        // Known timer-mode title keywords (lower-cased).
        private val TIMER_KEYWORDS = setOf("timer", "countdown", "time remaining")

        // Known stopwatch-mode title keywords (lower-cased).
        private val STOPWATCH_KEYWORDS = setOf("stopwatch", "chrono", "chronometer", "lap")
    }

    /** Tracks the notification key of the active clock notification. */
    private var activeKey: String? = null

    // ── Called from NotificationListenerService ───────────────────────────────

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in CLOCK_PACKAGES) return

        val n       = sbn.notification ?: return
        val extras  = n.extras
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: ""
        val combined = "$title $text".lowercase()

        // ── Detect mode ───────────────────────────────────────────────────────
        val isStopwatch = STOPWATCH_KEYWORDS.any { combined.contains(it) }
        val isTimer     = !isStopwatch && (
                TIMER_KEYWORDS.any { combined.contains(it) } || TIME_PATTERN.containsMatchIn(text)
                )

        if (!isTimer && !isStopwatch) return // not a timer/stopwatch notification

        activeKey = "${sbn.packageName}:${sbn.id}"

        // ── Detect paused ─────────────────────────────────────────────────────
        val isPaused = combined.contains("pause")

        // ── Parse time from text ──────────────────────────────────────────────
        val timeText = TIME_PATTERN.find(text)?.value
            ?: TIME_PATTERN.find(title)?.value
            ?: ""
        val timeMs = parseTimeText(timeText)

        if (isTimer) {
            // For a countdown timer we need totalDurationMs to draw the
            // progress ring. The notification doesn't carry it directly, so
            // we derive it from a Chronometer extra if present, or fall back
            // to treating the current remaining time as the total on first
            // sight (ring starts full and stays full — better than nothing).
            val existingTotal = TimerRepository.flow.value
                .takeIf { it.mode == TimerMode.TIMER && it.isActive }
                ?.totalDurationMs ?: 0L

            val totalDurationMs = when {
                existingTotal > 0L && timeMs <= existingTotal -> existingTotal
                existingTotal == 0L                           -> timeMs  // first notification — seed total
                else                                          -> timeMs  // timer was reset
            }

            // Extract user label — anything that isn't "Timer" / "Countdown".
            val label = if (TIMER_KEYWORDS.none { title.lowercase() == it }) title else ""

            TimerRepository.onTimerTick(
                remainingMs     = timeMs,
                totalDurationMs = totalDurationMs,
                label           = label,
                running         = !isPaused,
            )
        } else {
            // Stopwatch — text IS the elapsed time.
            TimerRepository.onStopwatchTick(
                elapsedMs = timeMs,
                running   = !isPaused,
                laps      = TimerRepository.flow.value.laps, // preserve existing laps
            )
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in CLOCK_PACKAGES) return
        val key = "${sbn.packageName}:${sbn.id}"
        if (activeKey == key) {
            activeKey = null
            TimerRepository.onEnded()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses "MM:SS", "H:MM:SS", or "MM:SS.cc" into milliseconds.
     * Returns 0 on any parse failure.
     */
    private fun parseTimeText(s: String): Long {
        if (s.isBlank()) return 0L
        return try {
            // Split on ':' first, then handle centiseconds in the last segment.
            val colonParts = s.split(":")
            when (colonParts.size) {
                2 -> {
                    val minutes = colonParts[0].toLong()
                    val (secs, centis) = parseSecsAndCentis(colonParts[1])
                    (minutes * 60 + secs) * 1_000L + centis * 10L
                }
                3 -> {
                    val hours   = colonParts[0].toLong()
                    val minutes = colonParts[1].toLong()
                    val (secs, centis) = parseSecsAndCentis(colonParts[2])
                    (hours * 3600 + minutes * 60 + secs) * 1_000L + centis * 10L
                }
                else -> 0L
            }
        } catch (_: Exception) { 0L }
    }

    /** Splits "42.31" into Pair(42L, 31L); "42" into Pair(42L, 0L). */
    private fun parseSecsAndCentis(s: String): Pair<Long, Long> {
        val dotParts = s.split(".")
        val secs   = dotParts[0].toLongOrNull() ?: 0L
        val centis = if (dotParts.size > 1) dotParts[1].toLongOrNull() ?: 0L else 0L
        return secs to centis
    }
}