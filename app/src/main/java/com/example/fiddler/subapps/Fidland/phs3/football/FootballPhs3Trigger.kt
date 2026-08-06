package com.example.fiddler.subapps.Fidland.phs3.football

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FootballPhs3Trigger — lifecycle entry point for the live-football phs3 module.
 *
 * Mirrors the pattern established by MusicPhs3Trigger:
 *   • Owns a [FootballRepository] and a [FootballPhs3Handler].
 *   • Calls [FidlandService.activatePhs3] when at least one tracked match is
 *     live or kicking off within [ACTIVATE_WINDOW_MS].
 *   • Calls [FidlandService.deactivatePhs3] when no such match exists.
 *
 * ── §B7 build (this pass) — home Dominant/55 + match-event Special Condition ──
 * Resolves both build-status blockers from the design doc's Football entry
 * ("Extra-time status mechanism undefined; fouls/injuries data-source
 * unconfirmed"):
 *
 * • **Home bid** — Dominant, sub-score [FOOTBALL_DOMINANT_SUB_SCORE] (55,
 *   confirmed in §B7), submitted whenever any match is live/kicking-off-soon
 *   — i.e. for this trigger's entire registered lifetime, matching the
 *   doc's noted pattern ("Submissive home class, escalation threshold
 *   coincides with the qualify condition itself"). Football isn't wired
 *   into `Phs3Manager.policyOf` as EVENT_DRIVEN, so — same as Ring Mode —
 *   its home state just rides `Phs3Manager`'s normal continuous rotation
 *   via [FidlandService.activatePhs3]/[FidlandService.deactivatePhs3];
 *   only the match-event promotion below needs an explicit event-driven
 *   slot grab.
 * • **Match-event / status-transition Special Condition** — sub-score
 *   [FOOTBALL_SPECIAL_CONDITION_SUB_SCORE] (50, confirmed), standard
 *   [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] dwell per moment.
 *   Resolves the doc's open question ("does every qualifying event promote,
 *   or just the single best pick per poll") as **every one does** —
 *   [FootballRepository.specialMomentFlow] now emits one
 *   [FootballSpecialMoment] per new goal/card/sub *and* per status
 *   transition (kickoff/half-time/extra-time/full-time), not just the
 *   single highest-priority pick the location-a flash still uses. [drainMomentQueue]
 *   works through them one at a time, FIFO, each getting its own dwell
 *   instead of overwriting whichever fired last.
 * • **Status-transition detector** — new in [FootballRepository]; see
 *   [MatchStatusTransition]'s class doc for the extra-time minute-heuristic
 *   this uses instead of inventing a new [MatchStatus] value.
 * • **Fouls/injuries — still NOT built.** No source (FD/OLDB/AF) parses
 *   them and AF's free-tier coverage is unconfirmed, so this stays an open
 *   item rather than being guessed at, matching the doc's own flag.
 * • **Simultaneous events across different matches** — each promotes
 *   independently through the same FIFO queue (the doc's other open
 *   question); no per-match dedup or suppression is applied.
 *
 * ── Wire-up in FidlandService ─────────────────────────────────────────────────
 *
 *   private lateinit var footballTrigger: FootballPhs3Trigger
 *
 *   override fun onCreate() {
 *       super.onCreate()
 *       footballTrigger = FootballPhs3Trigger(
 *           context  = this,
 *           scope    = serviceScope,
 *           service  = this,
 *           fdApiKey = "YOUR_FOOTBALL_DATA_ORG_KEY",
 *           afApiKey = "YOUR_API_FOOTBALL_KEY",   // blank to disable AF overlay
 *       )
 *       footballTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       footballTrigger.stop()
 *       super.onDestroy()
 *   }
 *
 * ── Location-a wiring ─────────────────────────────────────────────────────────
 * [FootballPhs3Handler] opts in via hasLocationA = true / LocationAContent().
 * overlay_fidland_pill wires it automatically — no manual cast needed.
 *
 *   // Right zone (location b)
 *   activePhs3Handler?.Indicator()
 *
 * @param context   Application / service context forwarded to [FootballRepository]
 *                  for [AfRequestLog] persistence.
 * @param scope     CoroutineScope tied to FidlandService lifetime.
 * @param service   Reference to FidlandService for activate/deactivate calls.
 * @param fdApiKey  football-data.org free API key.
 *                  Obtain at https://www.football-data.org (no credit card).
 * @param afApiKey  api-football (api-sports.io) free API key.
 *                  Obtain at https://dashboard.api-football.com (no credit card).
 *                  Pass blank string to run without the AF overlay (FD + OLDB only).
 */
class FootballPhs3Trigger(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
    private val fdApiKey: String,
    private val afApiKey: String = "",
) {
    /**
     * Shared [FootballRepository] instance — exposed so the Football dashboard
     * tab (state 4) can read the same flows the pill (state 3 / State 5) reads,
     * rather than spinning up a second repository with its own polling loops
     * and its own api-football daily budget consumption.
     */
    val repo    = FootballRepository(context = context, scope = scope, apiKey = fdApiKey, afApiKey = afApiKey)
    private val handler = FootballPhs3Handler(repo = repo)

    private var watchJob: Job? = null
    private var momentJob: Job? = null

    /**
     * How far ahead of kick-off we activate the phs3 slot.
     * 30 min gives the UI time to show the line-up / upcoming match card.
     */
    private val ACTIVATE_WINDOW_MS = 30 * 60_000L

    /** True whenever ≥1 match is live/kicking-off-soon — used to build the Special-Condition fallback bid. */
    private var isQualified = false

    /** FIFO queue of not-yet-drained Special-Condition moments; [isDraining] guards against overlapping drains. */
    private val momentQueue = ArrayDeque<FootballSpecialMoment>()
    private var isDraining = false

    fun start() {
        Phs3DebugLog.onTriggerStart("Football")
        repo.start()

        watchJob = scope.launch {
            repo.matchesFlow.collect { matches ->
                val now  = System.currentTimeMillis()
                val live = matches.count { it.isActive() }
                val soon = matches.count { m ->
                    m.status == MatchStatus.SCHEDULED &&
                            (m.kickoffMs - now) in 0..ACTIVATE_WINDOW_MS
                }
                Phs3DebugLog.onPoll("Football", "total=${matches.size} live=$live soon=$soon")

                isQualified = live > 0 || soon > 0
                if (isQualified) {
                    service.activatePhs3(handler)
                    service.phs3Manager.scheduler.submit(homeBid())
                } else {
                    service.deactivatePhs3("Football")
                    service.phs3Manager.scheduler.withdraw(handler.label)
                    momentQueue.clear()
                }
            }
        }

        // Drains [FootballRepository.specialMomentFlow] — see class doc's
        // "every event promotes" resolution.
        momentJob = scope.launch {
            repo.specialMomentFlow.collect { moment ->
                momentQueue.addLast(moment)
                if (!isDraining) drainMomentQueue()
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Football")
        watchJob?.cancel()
        watchJob = null
        momentJob?.cancel()
        momentJob = null
        momentQueue.clear()
        isDraining = false
        service.phs3Manager.scheduler.withdraw(handler.label)
        repo.stop()
    }

    // ── Internal — priority bids ─────────────────────────────────────────────

    private fun homeBid(): Phs3Priority = Phs3Priority(
        handler       = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore      = FOOTBALL_DOMINANT_SUB_SCORE,
    )

    /**
     * Works through [momentQueue] one item at a time — each moment gets its
     * own [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS] dwell before the
     * next one (if any) takes over, or the slot reverts to [homeBid] /
     * continuous rotation once the queue drains empty.
     */
    private fun drainMomentQueue() {
        val moment = momentQueue.removeFirstOrNull()
        if (moment == null) {
            isDraining = false
            service.phs3Manager.resumeAfterEventDriven()
            return
        }
        isDraining = true
        Phs3DebugLog.onPoll("Football", "Special-Condition moment: ${moment.label()}")

        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler       = handler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore      = FOOTBALL_SPECIAL_CONDITION_SUB_SCORE,
                holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                fallback      = if (isQualified) homeBid() else null,
            )
        )
        service.phs3Manager.surfaceEventDriven(handler)

        scope.launch {
            delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
            drainMomentQueue()
        }
    }

    companion object {
        /** §B7, confirmed: "Football-live". */
        const val FOOTBALL_DOMINANT_SUB_SCORE = 55
        /** §B7, confirmed: "Football match-event". */
        const val FOOTBALL_SPECIAL_CONDITION_SUB_SCORE = 50
    }
}