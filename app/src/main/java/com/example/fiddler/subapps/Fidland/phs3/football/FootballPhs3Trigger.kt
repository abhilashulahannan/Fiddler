package com.example.fiddler.subapps.Fidland.phs3.football

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * [FootballPhs3Handler.LocationAIndicator] must be placed in the pill's left
 * zone composable (location a), the same slot used by AlbumArtSpinner for music.
 *
 *   // Left zone
 *   if (activePhs3Handler is FootballPhs3Handler) {
 *       (activePhs3Handler as FootballPhs3Handler).LocationAIndicator()
 *   }
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

    /**
     * How far ahead of kick-off we activate the phs3 slot.
     * 30 min gives the UI time to show the line-up / upcoming match card.
     */
    private val ACTIVATE_WINDOW_MS = 30 * 60_000L

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

                if (live > 0 || soon > 0) {
                    service.activatePhs3(handler)
                } else {
                    service.deactivatePhs3("Football")
                }
            }
        }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Football")
        watchJob?.cancel()
        watchJob = null
        repo.stop()
    }
}