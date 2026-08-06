package com.example.fiddler.subapps.Fidland.phs3.download

import android.content.Context
import android.content.SharedPreferences
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * DownloadPhs3Trigger — driven by [DownloadAggregator], which merges four
 * sources:
 *   1. NotificationDownloadSource  — Chrome, Firefox, WhatsApp, etc.
 *   2. DownloadManagerSource       — Play Store, system OTA
 *   3. TrafficStatsDownloadSource  — catch-all speed signal / fallback
 *   4. FileObserverDownloadSource  — completion detection
 *
 * ── §B7 build (this pass) — settings gate, home Dominant/45, two Special
 *    Conditions ──────────────────────────────────────────────────────────
 * Resolves the design doc's Download entry ("untouched this pass ...
 * confirm the 5-8s duration assumption; decide the 95-100% pulse-vs-held-
 * range shape; pick the filename block's side" — the last of those three is
 * resolved in [DownloadPhs3Handler]'s own class doc, not here):
 *
 * • **Bug fixed** — the `"phs3_download"` toggle existed in FidlandScreen.kt's
 *   settings UI but was never read; the trigger started unconditionally.
 *   [isEnabledInSettings] now gates every aggregator emission, and
 *   [prefsListener] reacts immediately to a mid-session toggle-off instead
 *   of waiting for the next emission — same shape as Calendar's Bug (1) fix.
 * • **Home bid** — Dominant, sub-score [DOWNLOAD_DOMINANT_SUB_SCORE] (45,
 *   per §B7 — "conditional Dominant, sub-score 45"), submitted for this
 *   trigger's entire registered lifetime, matching the doc's noted pattern
 *   ("home Submissive is never actually observed" — same shape as Call/
 *   Alarm/Timer/Nav/Football/Calendar's conditional-Dominant-only entities).
 *   Download isn't wired into `Phs3Manager.policyOf` as EVENT_DRIVEN, so its
 *   home state rides normal continuous rotation via `activatePhs3`/
 *   `deactivatePhs3`; only the two Special Conditions below need an explicit
 *   event-driven slot grab.
 * • **Special Condition (1) — download starts.** Duration: the doc's "5-8s
 *   assumed, confirm if different intended" is left as the shared
 *   [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] (6.5s, the scheduler's
 *   own documented midpoint of that "~10 entities" 5-8s convention, Download
 *   named explicitly among them) rather than a bespoke constant — flagged as
 *   the adopted default, not a re-confirmed number. **New logic, this
 *   pass:** the doc's identity-tracking gap ("today's trigger only
 *   distinguishes 'some download active' vs. 'none,' doesn't detect one
 *   download replacing another as primary without going through null
 *   first") is resolved by collecting [DownloadAggregator.primaryKey]
 *   alongside [DownloadAggregator.primaryDownload] and diffing
 *   [AggregatedDownload.key] against [previousKey] — same shape as
 *   Navigation's `previousInstruction` diff off `NavStep.instruction`. A
 *   higher-confidence source picking up a download the Traffic fallback was
 *   already showing (same underlying file, new key) now correctly re-fires
 *   the promotion, not just a null→non-null edge.
 * • **Special Condition (2) — reaches 95-100%.** Doc's open question
 *   ("one-time pulse on crossing 95%, or held for the whole 95-100% range")
 *   is resolved as **held for the whole range** — an indefinite hold
 *   (`holdMs = null`), same shape the doc itself points at ("same
 *   'indefinite hold' shape as Navigation's nearing-turn trigger"). Adopted
 *   as a default per the doc's own framing, not a confirmed spec answer — a
 *   one-time pulse was the other option considered, but a held range keeps
 *   "almost done" visible for what's usually only a few seconds anyway, and
 *   reuses an already-proven pattern instead of a bespoke timer. Latched
 *   per download identity via [ninetyFiveFiredForKey] (mirrors Navigation's
 *   `approachFiredForInstruction`) so re-polling the same download at 96%,
 *   97%, ... doesn't repeatedly resubmit the same bid; reverts to the
 *   standing Dominant home bid the moment a *different* download becomes
 *   primary (new [AggregatedDownload.key], handled by the same identity
 *   diff as Special Condition (1)) or the aggregator goes idle.
 *
 * ── Wire-up in FidlandService (unchanged) ─────────────────────────────────
 *   downloadTrigger = DownloadPhs3Trigger(this, serviceScope, this)
 *   downloadTrigger.start()
 *
 *   override fun onNotificationPosted(sbn) {
 *       downloadTrigger?.notificationSource?.onNotificationPosted(sbn)
 *       ...
 *   }
 *   override fun onNotificationRemoved(sbn) {
 *       downloadTrigger?.notificationSource?.onNotificationRemoved(sbn)
 *       ...
 *   }
 *
 * The trigger exposes [aggregator.notificationSource] so the
 * NotificationListenerService can feed it without needing a reference to
 * the whole trigger.
 */
class DownloadPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    val aggregator = DownloadAggregator(context, scope)

    /** Convenience accessor for NotificationListenerService wiring. */
    val notificationSource: NotificationDownloadSource
        get() = aggregator.notificationSource

    private var collectJob: Job? = null

    /** [AggregatedDownload.key] of the last-seen primary download — see class doc's identity diff. */
    private var previousKey: String? = null

    /** Key the 95-100% Special Condition already latched for — see class doc. */
    private var ninetyFiveFiredForKey: String? = null

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fidland_prefs", Context.MODE_PRIVATE)

    /** Reacts immediately to a mid-session settings toggle-off — see class doc's "Bug fixed" note. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SETTINGS_KEY && !isEnabledInSettings()) {
            deactivate()
        }
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Download")
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        aggregator.start()

        collectJob = combine(
            aggregator.primaryDownload,
            aggregator.primaryKey,
        ) { info, key -> info to key }
            .onEach { (info, key) -> onSnapshot(info, key) }
            .launchIn(scope)
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Download")
        collectJob?.cancel()
        collectJob = null
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) } catch (_: Exception) { }
        aggregator.stop()
        previousKey = null
        ninetyFiveFiredForKey = null
        service.phs3Manager.scheduler.withdraw("Download")
        service.deactivatePhs3("Download")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun onSnapshot(info: DownloadInfo?, key: String?) {
        if (!isEnabledInSettings()) {
            deactivate()
            return
        }

        if (info == null || key == null) {
            if (previousKey != null) {
                Phs3DebugLog.onPoll("Download", "no active download — deactivating")
            }
            deactivate()
            return
        }

        val handler = DownloadPhs3Handler(info)
        service.activatePhs3(handler)

        val dominantBid = homeBid(handler)
        val isNewDownload = key != previousKey

        Phs3DebugLog.onPoll(
            "Download",
            "key=$key title=\"${info.title}\" progress=${(info.progressFraction * 100).toInt()}% new=$isNewDownload"
        )

        // ── Special Condition (2) latch reset — a different download took
        //    over the primary slot, so the 95% latch is per-identity. ──────
        if (isNewDownload) {
            ninetyFiveFiredForKey = null
        }

        // ── Special Condition (1) — download starts / identity change ─────
        if (isNewDownload) {
            Phs3DebugLog.onPoll("Download", "download starts: \"${info.title}\"")
            service.phs3Manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = DOWNLOAD_SPECIAL_CONDITION_SUB_SCORE,
                    holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                    fallback      = dominantBid,
                )
            )
            service.phs3Manager.surfaceEventDriven(handler)
        } else {
            // Not a fresh identity — keep the standing home bid current
            // (fresh handler instance every emission, same as Football's
            // homeBid() re-submission) unless the 95-100% hold below
            // supersedes it this tick.
            service.phs3Manager.scheduler.submit(dominantBid)
        }
        previousKey = key

        // ── Special Condition (2) — reaches 95-100%, held for the range ───
        if (info.progressFraction >= NINETY_FIVE_PERCENT_THRESHOLD &&
            ninetyFiveFiredForKey != key
        ) {
            Phs3DebugLog.onPoll(
                "Download",
                "reached ${(info.progressFraction * 100).toInt()}% — promoting (held), latched"
            )
            ninetyFiveFiredForKey = key
            service.phs3Manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = DOWNLOAD_SPECIAL_CONDITION_SUB_SCORE,
                    holdMs        = null,
                    fallback      = null,
                )
            )
            service.phs3Manager.surfaceEventDriven(handler)
        }
    }

    private fun deactivate() {
        previousKey = null
        ninetyFiveFiredForKey = null
        service.phs3Manager.scheduler.withdraw("Download")
        service.deactivatePhs3("Download")
    }

    private fun homeBid(handler: DownloadPhs3Handler): Phs3Priority = Phs3Priority(
        handler       = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore      = DOWNLOAD_DOMINANT_SUB_SCORE,
    )

    // ── Internal — settings gating (bug fix) ──────────────────────────────

    private fun isEnabledInSettings(): Boolean = prefs.getBoolean(SETTINGS_KEY, false)

    companion object {
        private const val SETTINGS_KEY = "phs3_download"

        /** §B7, confirmed: "conditional Dominant, sub-score 45". */
        const val DOWNLOAD_DOMINANT_SUB_SCORE = 45

        /**
         * §B7: doc left this unnamed for Download specifically (only gives
         * Battery/Football/etc. their own confirmed numbers) — adopted at
         * the same "5-8s tier" band Navigation/Weather use for their own
         * unnamed Special Conditions (55), flagged as a default rather than
         * a re-confirmed figure. Shared by both Special Conditions here.
         */
        const val DOWNLOAD_SPECIAL_CONDITION_SUB_SCORE = 55

        /** §B7: "reaches 95-100%" — new threshold constant the doc asked for. */
        const val NINETY_FIVE_PERCENT_THRESHOLD = 0.95f
    }
}