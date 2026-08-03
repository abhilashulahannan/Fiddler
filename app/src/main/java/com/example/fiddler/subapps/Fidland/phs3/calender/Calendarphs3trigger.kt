package com.example.fiddler.subapps.Fidland.phs3.calender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Phs3 trigger — Calendar.
 *
 * Always-on trigger, same idiom as WeatherPhs3Trigger: queries the system
 * calendar provider ([CalendarContract.Instances]) on start and every
 * [REFRESH_INTERVAL_MS], then activates/deactivates [CalendarPhs3Handler]
 * with the result via `service.activatePhs3(...)` / `deactivatePhs3("Calendar")`.
 *
 * ── Data source ──────────────────────────────────────────────────────────
 *   Reads from Android's unified CalendarProvider — NOT a Google or Samsung
 *   SDK. Every calendar app (Google Calendar, Samsung Calendar, Outlook,
 *   etc.) that the user has added as an account on the device syncs its
 *   events into this same provider, so results here are a merge of all of
 *   them automatically. No per-provider integration needed.
 *
 * ── Permission ───────────────────────────────────────────────────────────
 *   Requires `Manifest.permission.READ_CALENDAR` (runtime + manifest — see
 *   PermissionsActivity.kt). If not granted, [start] no-ops (no crash, no
 *   handler activation) so the rest of the pill still works normally.
 *
 * ── Query window ─────────────────────────────────────────────────────────
 *   Pulls instances from "now" through the next 7 days, matching
 *   [groupEventsForState5]'s Today/Tomorrow/This week grouping. Deactivates
 *   the handler entirely when there are zero events in that window, mirroring
 *   MusicPhs3Trigger's null-track handling.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────
 *
 *   private lateinit var calendarTrigger: CalendarPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       calendarTrigger = CalendarPhs3Trigger(applicationContext, serviceScope, this)
 *       calendarTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       if (::calendarTrigger.isInitialized) calendarTrigger.stop()
 *       ...
 *   }
 *
 * @param context Android context — used for the ContentResolver query and
 *                 permission check.
 * @param scope   Service-scoped coroutine scope for the polling loop.
 * @param service The running [FidlandService], used to call activatePhs3 /
 *                 deactivatePhs3.
 */
class CalendarPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private var job: Job? = null

    fun start() {
        if (!hasCalendarPermission()) {
            // Permission not granted — skip entirely, no active loop.
            // Re-launch of FidlandService (e.g. after granting the
            // permission later) will pick this up on next start().
            return
        }

        job = scope.launch {
            while (true) {
                val events = withContext(Dispatchers.IO) { queryUpcomingEvents() }

                if (events.isEmpty()) {
                    service.deactivatePhs3("Calendar")
                } else {
                    service.activatePhs3(CalendarPhs3Handler(events))
                }

                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Queries [CalendarContract.Instances] for all events starting from now
     * through the next [QUERY_WINDOW_DAYS] days, across every calendar
     * account on the device (Google, Samsung, Outlook, etc. — whatever's
     * synced into the system provider).
     */
    private fun queryUpcomingEvents(): List<CalendarEvent> {
        val nowMs = System.currentTimeMillis()
        val windowEndMs = nowMs + QUERY_WINDOW_DAYS * DAY_MS

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(uriBuilder, nowMs)
        android.content.ContentUris.appendId(uriBuilder, windowEndMs)
        val uri = uriBuilder.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_COLOR,
            // VISIBLE excludes calendars the user has toggled off in their
            // calendar app — respects the same "hidden calendars" the user
            // set up in Google/Samsung Calendar.
            CalendarContract.Instances.VISIBLE,
        )

        val events = mutableListOf<CalendarEvent>()

        val cursor = try {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
        } catch (e: SecurityException) {
            // Permission revoked between the check and the query (rare race,
            // e.g. user pulls permission mid-session). Treat as no events.
            null
        }

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
            val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val locationIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val colorIdx = it.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)

            while (it.moveToNext()) {
                val title = if (titleIdx >= 0) it.getString(titleIdx).orEmpty() else ""
                val begin = if (beginIdx >= 0) it.getLong(beginIdx) else continue
                val end = if (endIdx >= 0) it.getLong(endIdx) else continue
                val allDay = allDayIdx >= 0 && it.getInt(allDayIdx) != 0
                val location = if (locationIdx >= 0) it.getString(locationIdx) else null
                val colorHex = if (colorIdx >= 0) {
                    runCatching { String.format("#%06X", 0xFFFFFF and it.getInt(colorIdx)) }.getOrNull()
                } else null

                events += CalendarEvent(
                    title = title,
                    startMs = begin,
                    endMs = end,
                    isAllDay = allDay,
                    location = location,
                    colorHex = colorHex,
                )
            }
        }

        return events
    }

    companion object {
        private const val QUERY_WINDOW_DAYS = 7L
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        /** Refresh cadence — matches WeatherPhs3Trigger's 15-minute idiom, but
         *  a bit tighter since calendar events change indicator text as they
         *  approach ("in 45m" -> "in 44m" isn't worth polling for, but a newly
         *  added/edited event should show up reasonably fast). */
        private const val REFRESH_INTERVAL_MS = 5L * 60L * 1000L
    }
}