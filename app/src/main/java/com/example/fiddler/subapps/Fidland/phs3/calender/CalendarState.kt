package com.example.fiddler.subapps.Fidland.phs3.calender

/**
 * Phs3 module — Calendar — shared state models.
 *
 * Wiring (future): populate [CalendarEvent] list from CalendarContract
 * (CalendarProvider) — query CalendarContract.Instances for the relevant
 * time range. See CalendarPhs3Trigger (TBD) for activation — mirrors
 * MusicPhs3Trigger's pattern of calling
 * `activatePhs3(CalendarPhs3Handler(...))` / `deactivatePhs3()`.
 */

/**
 * A single calendar event/instance.
 *
 * @param title      Event title.
 * @param startMs    Epoch-ms start time.
 * @param endMs      Epoch-ms end time.
 * @param isAllDay   True for all-day events — shown as "All day" instead of
 *                    a time range.
 * @param location   Optional location string, shown in State 5 if present.
 * @param colorHex   Optional calendar colour (e.g. "#7C8CFF") for the
 *                    State 5 leading dot. Falls back to a neutral grey.
 */
data class CalendarEvent(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val colorHex: String? = null
)

/** Remaining ms until [CalendarEvent.startMs], clamped to 0. */
fun CalendarEvent.startsInMs(nowMs: Long): Long =
    (startMs - nowMs).coerceAtLeast(0L)

/** True while [nowMs] falls between [CalendarEvent.startMs] and [endMs]. */
fun CalendarEvent.isOngoing(nowMs: Long): Boolean =
    nowMs in startMs until endMs

/**
 * Indicator subtitle text:
 *   - All-day event           → "All day"
 *   - Ongoing                 → "Now"
 *   - Starts within 60 min    → "in Xm" / "in 1h Xm"
 *   - Further out             → clock time, e.g. "9:15 AM"
 */
fun CalendarEvent.indicatorSubtitle(nowMs: Long): String {
    if (isAllDay) return "All day"
    if (isOngoing(nowMs)) return "Now"
    val remaining = startsInMs(nowMs)
    val totalMins = remaining / 60_000L
    return when {
        totalMins < 1L  -> "now"
        totalMins < 60L -> "in ${totalMins}m"
        totalMins < 24 * 60L -> {
            val h = totalMins / 60
            val m = totalMins % 60
            if (m == 0L) "in ${h}h" else "in ${h}h ${m}m"
        }
        else -> formatClockTime(startMs)
    }
}

/** Formats an epoch-ms time as "9:15 AM". */
fun formatClockTime(epochMs: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    var hour = cal.get(java.util.Calendar.HOUR)
    if (hour == 0) hour = 12
    val minute = cal.get(java.util.Calendar.MINUTE)
    val amPm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(hour, minute, amPm)
}

/** Formats an event's time range for State 5, e.g. "9:00 – 9:30 AM" or "All day". */
fun CalendarEvent.timeRangeLabel(): String {
    if (isAllDay) return "All day"
    return "${formatClockTime(startMs)} – ${formatClockTime(endMs)}"
}

/** Section grouping for the State 5 list. */
enum class CalendarSection(val title: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    THIS_WEEK("Upcoming this week")
}

/** A section header + its events, for State 5's grouped list. */
data class CalendarSectionGroup(
    val section: CalendarSection,
    val events: List<CalendarEvent>
)

/**
 * Splits [events] into Today / Tomorrow / This week (next 7 days, excluding
 * today and tomorrow) sections relative to [nowMs], dropping empty sections.
 * Each section's events are sorted by start time.
 */
fun groupEventsForState5(
    events: List<CalendarEvent>,
    nowMs: Long = System.currentTimeMillis()
): List<CalendarSectionGroup> {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis

    val dayMs = 24L * 60L * 60L * 1000L
    val tomorrowStart = todayStart + dayMs
    val dayAfterTomorrowStart = todayStart + 2 * dayMs
    val weekEnd = todayStart + 7 * dayMs

    val today = mutableListOf<CalendarEvent>()
    val tomorrow = mutableListOf<CalendarEvent>()
    val week = mutableListOf<CalendarEvent>()

    for (event in events) {
        when {
            event.startMs < tomorrowStart && event.endMs > nowMs ->
                today += event
            event.startMs in tomorrowStart until dayAfterTomorrowStart ->
                tomorrow += event
            event.startMs in dayAfterTomorrowStart until weekEnd ->
                week += event
        }
    }

    val byStart = compareBy<CalendarEvent> { it.startMs }
    return listOfNotNull(
        if (today.isNotEmpty()) CalendarSectionGroup(CalendarSection.TODAY, today.sortedWith(byStart)) else null,
        if (tomorrow.isNotEmpty()) CalendarSectionGroup(CalendarSection.TOMORROW, tomorrow.sortedWith(byStart)) else null,
        if (week.isNotEmpty()) CalendarSectionGroup(CalendarSection.THIS_WEEK, week.sortedWith(byStart)) else null,
    )
}

/**
 * The "next" event to show in the State 3 indicator: the soonest event that
 * hasn't ended yet (ongoing or upcoming), or null if there's nothing left
 * today/queued.
 */
fun nextIndicatorEvent(
    events: List<CalendarEvent>,
    nowMs: Long = System.currentTimeMillis()
): CalendarEvent? =
    events.filter { it.endMs > nowMs }.minByOrNull { it.startMs }