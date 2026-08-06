package com.example.fiddler.subapps.Fidland.phs3.calender

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phs3 trigger — Calendar.
 *
 * Always-on trigger, same idiom as WeatherPhs3Trigger: queries the system
 * calendar provider ([CalendarContract.Instances]) on start and every
 * [REFRESH_INTERVAL_MS], then activates/deactivates [CalendarPhs3Handler]
 * with the result via `service.activatePhs3(...)` / `deactivatePhs3("Calendar")`.
 *
 * ── §B7 build (this pass) — home Dominant/35 + "phone in use" Special Condition ──
 * Resolves both build-status blockers from the design doc's Calendar entry
 * ("'Phone in use' signal doesn't exist yet; 2 current-code bugs still open"):
 *
 * • **Bug (1) fixed** — the `"phs3_calendar"` toggle existed in
 *   FidlandScreen.kt's settings UI but [CalendarPhs3Trigger.start] never
 *   read it, so Calendar activated on permission alone. Now [isEnabledInSettings]
 *   gates every poll, and [prefsListener] reacts immediately to a mid-session
 *   toggle-off instead of waiting up to [REFRESH_INTERVAL_MS] for the next
 *   poll to notice (toggling back on still picks up on the next poll — same
 *   cadence as any other calendar edit).
 *   Bug (2) — the settings-copy claiming a configurable lead-time threshold
 *   that doesn't actually exist — is a strings/UI-copy fix outside this
 *   file; not touched here.
 * • **"Phone in use" signal — new.** Resolved as [Intent.ACTION_USER_PRESENT]
 *   (fires once the device is genuinely unlocked), not
 *   [Intent.ACTION_SCREEN_ON]. The design doc's open question was whether
 *   an always-on-display glance or an unrelated notification wake should
 *   count — `ACTION_USER_PRESENT` only fires on an actual unlock, which is
 *   the closest stock-Android signal to "sustained interactive use" without
 *   hooking into per-OEM AOD APIs. Flagged as an adopted default per the
 *   doc's own framing, not a confirmed spec answer.
 * • **Home bid** — Dominant, sub-score [CALENDAR_DOMINANT_SUB_SCORE] (35,
 *   confirmed), submitted continuously while ≥1 event qualifies in the
 *   7-day window. Calendar isn't wired into `Phs3Manager.policyOf` as
 *   EVENT_DRIVEN, so — same as Ring Mode/Football — its home state rides
 *   normal continuous rotation; only the phone-unlock promotion needs an
 *   explicit event-driven slot grab.
 * • **Special Condition** — phone-unlock AND an upcoming event exists,
 *   sub-score [CALENDAR_SPECIAL_CONDITION_SUB_SCORE]. The design doc left
 *   this "in the ~55-range tier" without a final number — 55 is adopted
 *   here (same tier as Music-track-change / Ring-Mode-change), flagged as
 *   a default, not a confirmed figure. Standard
 *   [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] dwell.
 * • **New per-entity 2h cooldown** — [MIN_PROMOTION_INTERVAL_MS], tracked
 *   locally via [lastPromotionAtMs]. The design doc floated generalizing
 *   this into [Phs3Priority] as a shared "min-interval" field, but that
 *   would mean touching the scheduler for every other entity too; a local
 *   timestamp check gets Calendar the same behavior without that shared-infra
 *   change, and is easy to lift into the scheduler later if a second entity
 *   ends up needing the same shape.
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
 *                 deactivatePhs3 and to reach `phs3Manager` for priority bids.
 */
class CalendarPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    private var pollJob: Job? = null

    /** Most recent poll's events — read by [onPhoneInUse] between polls. */
    private var latestEvents: List<CalendarEvent> = emptyList()

    /** Wall-clock time of the last "phone in use" promotion — gates [MIN_PROMOTION_INTERVAL_MS]. */
    private var lastPromotionAtMs: Long = 0L

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)

    /** Reacts immediately to a mid-session settings toggle — see class doc's "Bug (1) fixed" note. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SETTINGS_KEY && !isEnabledInSettings()) {
            service.deactivatePhs3("Calendar")
            service.phs3Manager.scheduler.withdraw("Calendar")
            latestEvents = emptyList()
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            onPhoneInUse()
        }
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Calendar")
        if (!hasCalendarPermission()) {
            // Permission not granted — skip entirely, no active loop.
            // Re-launch of FidlandService (e.g. after granting the
            // permission later) will pick this up on next start().
            return
        }

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(unlockReceiver, filter)
        }

        pollJob = scope.launch {
            while (true) {
                if (isEnabledInSettings()) {
                    val events = withContext(Dispatchers.IO) { queryUpcomingEvents() }
                    latestEvents = events
                    Phs3DebugLog.onPoll("Calendar", "events=${events.size}")

                    if (events.isEmpty()) {
                        service.deactivatePhs3("Calendar")
                        service.phs3Manager.scheduler.withdraw("Calendar")
                    } else {
                        val freshHandler = CalendarPhs3Handler(events)
                        service.activatePhs3(freshHandler)
                        service.phs3Manager.scheduler.submit(homeBid(freshHandler))
                    }
                } else {
                    latestEvents = emptyList()
                    service.deactivatePhs3("Calendar")
                    service.phs3Manager.scheduler.withdraw("Calendar")
                }

                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Calendar")
        pollJob?.cancel()
        pollJob = null
        try { context.unregisterReceiver(unlockReceiver) } catch (_: Exception) { }
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) } catch (_: Exception) { }
        service.phs3Manager.scheduler.withdraw("Calendar")
    }

    // ── Internal — settings gating (bug 1 fix) ────────────────────────────

    private fun isEnabledInSettings(): Boolean = prefs.getBoolean(SETTINGS_KEY, false)

    // ── Internal — "phone in use" Special Condition ───────────────────────

    /**
     * Fires on every [Intent.ACTION_USER_PRESENT] (genuine unlock). Promotes
     * only if the settings toggle is on, an upcoming event actually exists
     * (qualify still governs whether there's anything worth showing), and
     * [MIN_PROMOTION_INTERVAL_MS] has elapsed since the last promotion.
     */
    private fun onPhoneInUse() {
        if (!isEnabledInSettings()) return
        val nextEvent = nextIndicatorEvent(latestEvents) ?: return

        val now = System.currentTimeMillis()
        if (now - lastPromotionAtMs < MIN_PROMOTION_INTERVAL_MS) return
        lastPromotionAtMs = now

        val freshHandler = CalendarPhs3Handler(latestEvents)
        Phs3DebugLog.onPoll("Calendar", "phone-in-use promotion: ${nextEvent.title}")
        service.activatePhs3(freshHandler)
        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler       = freshHandler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore      = CALENDAR_SPECIAL_CONDITION_SUB_SCORE,
                holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                fallback      = homeBid(freshHandler),
            )
        )
        service.phs3Manager.surfaceEventDriven(freshHandler)
        scope.launch {
            delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
            service.phs3Manager.resumeAfterEventDriven()
        }
    }

    private fun homeBid(handler: CalendarPhs3Handler): Phs3Priority = Phs3Priority(
        handler       = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore      = CALENDAR_DOMINANT_SUB_SCORE,
    )

    // ── Internal — permission + query (unchanged) ─────────────────────────

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

        private const val SETTINGS_KEY = "phs3_calendar"

        /** §B7: new per-entity "phone in use" cooldown — see class doc. */
        const val MIN_PROMOTION_INTERVAL_MS = 2L * 60L * 60L * 1000L

        /** §B7, confirmed: Calendar home class. */
        const val CALENDAR_DOMINANT_SUB_SCORE = 35

        /** §B7: adopted default — doc left this "in the ~55-range tier" without a final confirmed number. */
        const val CALENDAR_SPECIAL_CONDITION_SUB_SCORE = 55
    }
}