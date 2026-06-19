package com.example.fiddler.subapps.Fidland.phs3.football

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * FootballRepository — four-source orchestrator.
 *
 * ── Source roles ──────────────────────────────────────────────────────────────
 *
 * football-data.org (FD)  — SCHEDULE OF RECORD
 *   Polled every [FD_POLL_INTERVAL_MS] (1 min). One request covers all 8
 *   competitions. Provides today's fixture list, kick-off times, and baseline
 *   status/score. Every FD poll triggers [recomputeDailyPlan] so the AF call
 *   schedule adapts to postponements and added fixtures in real time.
 *   Free plan gives ~5 min live-data delay and no per-match event detail.
 *
 * OpenLigaDB (OLDB)  — STEADY-STATE LIVE FALLBACK + gap filler
 *   Polled every [OLDB_IDLE_INTERVAL_MS] (2 min) by default. Switches to
 *   [OLDB_LIVE_INTERVAL_MS] (1 min) when it detects any live match in its own
 *   data, then resets to idle once live matches are gone. Covers Bundesliga
 *   with near-real-time goals. Also fills transitional gaps between AF windows.
 *
 * api-football (AF)  — BUDGETED HIGH-FIDELITY OVERLAY
 *   NOT on a fixed timer. The [startAfLoop] coroutine wakes on a context-aware
 *   schedule (15 s inside windows, 1 min pre-window, 10 min idle) and fires a
 *   real HTTP call only when a [PlannedPoll] timestamp from [currentPlan] has
 *   been reached AND [AfRequestLog.canFire] clears it (90 s spacing + 100/day cap).
 *   One call fetches /fixtures?live=all — all leagues at once.
 *   AF data takes precedence over FD/OLDB in the merge.
 *
 * TheSportsDB (TSDB)  — PREFERRED LOGO SOURCE
 *   Never polled for live data. [triggerImageBackfill] fires a throttled,
 *   cached lookup for EVERY team appearing in today's matches (not just ones
 *   missing a logo) because TSDB serves properly-sized badges (we use the
 *   "/small", 250px variant) whereas FD/OLDB/AF each only expose a single
 *   fixed-size crest URL. Once cached, a TSDB badge overrides whatever
 *   FD/OLDB/AF provided for that team — see the logo-overlay step in
 *   [mergeMatches]. Throttle: minimum 30 s between calls. Cache: per-process
 *   lifetime, so each team is only ever fetched once.
 *
 * ── Merge precedence (highest wins per field) ─────────────────────────────────
 *   Live data (score / minute / status / events): api-football > OpenLigaDB > football-data.org
 *   Logos: TheSportsDB (if cached) > whichever of AF/OLDB/FD provided one
 *
 * ── Polling strategy ─────────────────────────────────────────────────────────
 *   Both FD and OLDB loops are gated by [nextFdDelayMs] / [nextOldbDelayMs]
 *   which compute a context-aware sleep duration so neither source hammers the
 *   network when nothing is happening:
 *
 *     No matches today          → FD polls every 15 min; OLDB pauses entirely
 *     Pre-match (>2 h away)     → FD polls every 5 min; OLDB pauses
 *     Approaching (<2 h away)   → FD polls every 2 min; OLDB polls every 5 min
 *     Inside active window      → FD polls every 1 min; OLDB 1–2 min (as before)
 *     Post-match (all done)     → FD polls every 15 min until midnight
 *
 * ── Rate budget summary (worst case — match day, inside window) ──────────────
 *   FD:   1 req/min during window  → well under 10/min cap          ✓
 *   OLDB: 1 req/1–2 min during window, paused otherwise             ✓
 *   AF:   ≤95 planned calls/day, only inside active windows         ✓
 *   TSDB: ≥30 s apart, but now fetched for EVERY distinct team seen
 *         today (eager, not last-resort) — still bounded and cheap
 *         because each team is looked up at most ONCE per process
 *         lifetime (permanent cache) regardless of how many days or
 *         polls follow. Worst case is ~2 × (teams in today's fixtures)
 *         calls spread across however long it takes at 30 s apart —
 *         e.g. 8 simultaneous matches ≈ 16 teams ≈ 8 min to fully
 *         resolve on a cold cache, one-time per process start.       ✓
 *
 * @param context   Application / service context (forwarded to [ApiFootballSource]
 *                  for [AfRequestLog] file I/O, to [TheSportsDbSource] for the same
 *                  reason, and to a shared [FootballApiRequestLog] used purely for
 *                  the "requests sent today" counter shown in the Football settings
 *                  screen — it does not gate or throttle any call).
 * @param scope     CoroutineScope owned by FootballPhs3Trigger / FidlandService.
 * @param apiKey    football-data.org API key (X-Auth-Token header).
 * @param afApiKey  api-football (api-sports.io) API key. Pass blank to disable AF.
 */
class FootballRepository(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val apiKey: String,
    private val afApiKey: String = "",
) {

    // ── Public state ──────────────────────────────────────────────────────────

    /** Merged, de-duplicated list of today's matches from all sources. */
    private val _matchesFlow = MutableStateFlow<List<FootballMatch>>(emptyList())
    val matchesFlow: StateFlow<List<FootballMatch>> = _matchesFlow

    /** Emits a [FlashEvent] whenever a new goal or card is first detected. */
    private val _flashEventFlow = MutableStateFlow<FlashEvent?>(null)
    val flashEventFlow: StateFlow<FlashEvent?> = _flashEventFlow

    /** Today's AF call plan — exposed for debug/logging from the trigger. */
    private val _dailyPlanFlow = MutableStateFlow<FootballScheduleEngine.DailyPollPlan?>(null)
    val dailyPlanFlow: StateFlow<FootballScheduleEngine.DailyPollPlan?> = _dailyPlanFlow

    // ── Internal state ────────────────────────────────────────────────────────

    private var fdJob: Job? = null
    private var oldbJob: Job? = null
    private var afJob: Job? = null

    /** Event IDs we have already notified about — never fire the same event twice. */
    private val seenEventIds = mutableSetOf<String>()

    /** Latest parsed snapshots from each source — merged on every update. */
    private var latestFdMatches: List<FootballMatch>   = emptyList()
    private var latestOldbMatches: List<FootballMatch> = emptyList()
    private var latestAfMatches: List<FootballMatch>   = emptyList()

    /** Tracks whether OLDB's OWN current data includes any live match. */
    private var oldbHasSeenLiveData = false

    /** Current day's AF poll plan; null until first FD poll completes. */
    private var currentPlan: FootballScheduleEngine.DailyPollPlan? = null

    /** Set of PlannedPoll.atMs values already fired this session (guards against duplicates). */
    private val firedPollTimestamps = mutableSetOf<Long>()

    private val afSource = ApiFootballSource(context = context, apiKey = afApiKey)
    val sportsDb = TheSportsDbSource(context = context)

    /** Cross-source counter backing the "requests sent today" display in settings. */
    private val sharedRequestLog: FootballApiRequestLog = FootballApiRequestLog(context)

    companion object {
        private const val TAG = "FootballRepo"

        // FD interval tiers
        private const val FD_LIVE_MS          =      60_000L  // inside active window
        private const val FD_APPROACHING_MS   =  2 * 60_000L  // <2 h to first kickoff
        private const val FD_PRE_MATCH_MS     =  5 * 60_000L  // matches today, >2 h away
        private const val FD_QUIET_MS         = 15 * 60_000L  // no matches today / all done

        // OLDB interval tiers (Bundesliga only)
        private const val OLDB_LIVE_MS        =      60_000L  // live match detected by OLDB itself
        private const val OLDB_IDLE_MS        =  2 * 60_000L  // inside window, no live data yet
        private const val OLDB_APPROACHING_MS =  5 * 60_000L  // <2 h to Bundesliga kickoff
        // OLDB_PAUSE: effectively disabled; Long.MAX_VALUE/2 avoids overflow in delay()
        private const val OLDB_PAUSE_MS       = Long.MAX_VALUE / 2

        // How far ahead we wake OLDB before a Bundesliga window opens
        private const val OLDB_WAKE_BEFORE_MS = 2 * 60 * 60_000L   // 2 h

        private const val FD_BASE   = "https://api.football-data.org/v4"
        private const val OLDB_BASE = "https://api.openligadb.de"

        private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val ISO_FMT_OFFSET = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun start() {
        startFdLoop()
        startOldbLoop()
        startAfLoop()
    }

    fun stop() {
        fdJob?.cancel();  fdJob  = null
        oldbJob?.cancel(); oldbJob = null
        afJob?.cancel();  afJob  = null
    }

    // =========================================================================
    //  Loop 1 — football-data.org (schedule of record)
    // =========================================================================

    private fun startFdLoop() {
        if (fdJob?.isActive == true) return
        fdJob = scope.launch {
            while (true) {
                val matches = fetchFromFootballData()
                latestFdMatches = matches
                recomputeDailyPlan(matches)
                publishMerged()
                delay(nextFdDelayMs())
            }
        }
    }

    /**
     * Context-aware FD sleep duration.
     *
     * | Situation                              | Interval |
     * |----------------------------------------|----------|
     * | Inside an active match window          |    1 min |
     * | Within 2 h of first kickoff today      |    2 min |
     * | Matches exist today but >2 h away      |    5 min |
     * | No matches today (or all finished)     |   15 min |
     */
    private fun nextFdDelayMs(): Long {
        val plan = currentPlan ?: return FD_QUIET_MS
        val now  = System.currentTimeMillis()

        if (FootballScheduleEngine.isInActiveWindow(plan, now)) return FD_LIVE_MS

        val nextWindowStart = plan.windows
            .map { it.startMs }
            .filter { it > now }
            .minOrNull()

        if (nextWindowStart == null) return FD_QUIET_MS                       // all windows past
        val timeUntil = nextWindowStart - now
        return when {
            timeUntil <= 2 * 60 * 60_000L -> FD_APPROACHING_MS               // within 2 h
            else                          -> FD_PRE_MATCH_MS                 // >2 h away
        }
    }

    /**
     * Recompute — or reconcile — today's [FootballScheduleEngine.DailyPollPlan]
     * from the latest FD schedule. Called on every FD poll.
     *
     * If no plan exists yet, or if the stored plan is for yesterday, a fresh
     * plan is computed and the AF daily counter is reset. Otherwise the
     * existing plan is reconciled (already-fired calls are preserved; remaining
     * budget is redistributed over remaining window time).
     */
    private fun recomputeDailyPlan(fdMatches: List<FootballMatch>) {
        val today = todayUtcString()

        val todayKickoffs = fdMatches
            .filter { utcDateString(it.kickoffMs) == today }
            .map { it.kickoffMs }

        val fresh = FootballScheduleEngine.computeForToday(
            dayUtcDate = today,
            kickoffsMs = todayKickoffs,
        )

        val existing = currentPlan
        val reconciled = if (existing != null && existing.dayUtcDate == today) {
            // Mark polls we have already fired so reconcile can account for them.
            val planWithFiredFlags = existing.copy(
                polls = existing.polls.map { p ->
                    if (p.atMs in firedPollTimestamps) p.copy(fired = true) else p
                }
            )
            planWithFiredFlags.reconcileWith(fresh, System.currentTimeMillis())
        } else {
            // New day or first run — clear fired-timestamp memory too.
            firedPollTimestamps.clear()
            fresh
        }

        currentPlan = reconciled
        _dailyPlanFlow.value = reconciled

        android.util.Log.d(
            TAG,
            "plan[$today]: ${reconciled.windows.size} windows, " +
                    "${reconciled.polls.size} AF polls pending, " +
                    "${afSource.callsUsedToday} calls used today"
        )
    }

    // =========================================================================
    //  Loop 2 — OpenLigaDB (live fallback + gap filler)
    // =========================================================================

    private fun startOldbLoop() {
        if (oldbJob?.isActive == true) return
        oldbJob = scope.launch {
            while (true) {
                val matches = fetchFromOpenLigaDb()
                latestOldbMatches = matches

                // Track whether OLDB itself sees live data to adjust poll cadence.
                val anyLive = matches.any { it.isActive() }
                if (!oldbHasSeenLiveData && anyLive) {
                    oldbHasSeenLiveData = true
                    android.util.Log.d(TAG, "OLDB observed live data — switching to 1 min cadence")
                }
                if (oldbHasSeenLiveData && !anyLive) {
                    oldbHasSeenLiveData = false
                    android.util.Log.d(TAG, "OLDB no longer sees live data — back to 2 min cadence")
                }

                publishMerged()
                delay(if (oldbHasSeenLiveData) OLDB_LIVE_MS else OLDB_IDLE_MS)
            }
        }
    }

    // =========================================================================
    //  Loop 3 — api-football (budgeted high-fidelity overlay)
    // =========================================================================

    /**
     * Wakes on a context-aware schedule and fires a real HTTP call ONLY when a
     * [FootballScheduleEngine.PlannedPoll] timestamp is due, not yet fired, AND
     * [AfRequestLog.canFire] gives the green light (90 s spacing + daily cap).
     *
     * Sleep duration between iterations is determined by [ApiFootballSource.nextCheckDelayMs]:
     *   inside window  →  15 s   (polls may be due soon)
     *   pre-window     →   1 min (wake before the window opens)
     *   idle           →  10 min (nothing imminent; save battery)
     */
    private fun startAfLoop() {
        if (afApiKey.isBlank()) {
            android.util.Log.d(TAG, "AF key blank — api-football overlay disabled")
            return
        }
        if (afJob?.isActive == true) return

        afJob = scope.launch {
            while (true) {
                val plan     = currentPlan
                val now      = System.currentTimeMillis()
                val inWindow = FootballScheduleEngine.isInActiveWindow(plan, now)

                if (plan != null) {
                    // Find the earliest due poll that we haven't fired yet.
                    val due = plan.polls.firstOrNull { poll ->
                        !poll.fired && poll.atMs !in firedPollTimestamps && poll.atMs <= now
                    }

                    if (due != null) {
                        firedPollTimestamps += due.atMs
                        android.util.Log.d(
                            TAG,
                            "AF poll due (window ${due.windowIndex}, " +
                                    "slot ${due.atMs}, calls today: ${afSource.callsUsedToday})"
                        )
                        val fixtures = afSource.fetchLiveFixtures(inWindow = inWindow)
                        if (fixtures != null) {
                            latestAfMatches = fixtures
                            publishMerged()
                        }
                    }
                }

                delay(afSource.nextCheckDelayMs(plan))
            }
        }
    }

    // =========================================================================
    //  Merge — combine FD + OLDB + AF + TSDB into one canonical list
    // =========================================================================

    /**
     * Merge all four sources into one list, keyed by [FootballMatch.fixtureKey]
     * so the same real-world match from multiple sources collapses into ONE entry.
     *
     * Live-data precedence (lowest → highest; later writes win):
     *   football-data.org → OpenLigaDB → api-football
     *
     * [mergeOnto] performs field-level merging so we never lose data — e.g. if
     * AF has a score but null logo, we keep FD's logo.
     *
     * Logos are handled separately, as a final overlay pass: TheSportsDB wins
     * whenever it has a cached badge for the team, regardless of what FD/OLDB/AF
     * provided — TSDB offers a properly-sized image (see [TheSportsDbSource])
     * where the other three only ever give a single fixed-size URL. The lookup
     * is read-only here ([TheSportsDbSource.cachedBadge] never makes a network
     * call); population of the cache happens asynchronously in
     * [triggerImageBackfill]. Because this overlay runs on every merge — not
     * just the merge that triggered the backfill — a badge resolved by a slow
     * lookup is picked up by the very next poll from ANY source, instead of
     * racing a separate patch step.
     */
    private fun mergeMatches(): List<FootballMatch> {
        val byKey = linkedMapOf<String, FootballMatch>()

        for (m in latestFdMatches)   byKey[m.fixtureKey()] = m
        for (m in latestOldbMatches) byKey[m.fixtureKey()] = mergeOnto(byKey[m.fixtureKey()], m)
        for (m in latestAfMatches)   byKey[m.fixtureKey()] = mergeOnto(byKey[m.fixtureKey()], m)

        val withTsdbLogos = byKey.values.map { m ->
            val home = sportsDb.cachedBadge(m.homeTeam) ?: m.homeLogoUrl
            val away = sportsDb.cachedBadge(m.awayTeam) ?: m.awayLogoUrl
            if (home != m.homeLogoUrl || away != m.awayLogoUrl) {
                m.copy(homeLogoUrl = home, awayLogoUrl = away)
            } else m
        }

        return withTsdbLogos.sortedWith(
            compareByDescending<FootballMatch> { it.isActive() }.thenBy { it.kickoffMs }
        )
    }

    /**
     * Overlay [incoming] (higher precedence) onto [base] (lower precedence).
     *
     * Rules:
     * - Logos: prefer incoming if present, else fall back to base. (TSDB is
     *   layered on top of this result separately — see [mergeMatches].)
     * - Scores / minute / status / events: incoming wins if it has a value,
     *   else base is kept. This means AF's null awayScore during extra time
     *   won't overwrite a valid score from FD.
     * - kickoffMs: base (FD) wins if base has a value — FD's kickoff times
     *   are authoritative; AF's /fixtures?live=all returns an estimated kickoff.
     */
    internal fun mergeOnto(base: FootballMatch?, incoming: FootballMatch): FootballMatch {
        if (base == null) return incoming
        return incoming.copy(
            homeLogoUrl = incoming.homeLogoUrl ?: base.homeLogoUrl,
            awayLogoUrl = incoming.awayLogoUrl ?: base.awayLogoUrl,
            homeScore   = incoming.homeScore   ?: base.homeScore,
            awayScore   = incoming.awayScore   ?: base.awayScore,
            minute      = incoming.minute      ?: base.minute,
            events      = incoming.events.ifEmpty { base.events },
            kickoffMs   = base.kickoffMs,   // FD is authoritative for kick-off time
            venue       = incoming.venue    ?: base.venue,     // only FD provides this — preserve it
            matchday    = incoming.matchday ?: base.matchday,  // only FD provides this — preserve it
        )
    }

    private fun publishMerged() {
        val merged = mergeMatches()
        _matchesFlow.value = merged
        detectAndEmitNewEvents(merged)
        triggerImageBackfill(merged)
    }

    // =========================================================================
    //  Flash-event detection
    // =========================================================================

    /**
     * Scan [matches] for any [MatchEvent] whose ID has not been seen before.
     * Picks the single highest-priority new event (red > goal > yellow) and
     * emits it into [_flashEventFlow] so [FootballPhs3Handler.LocationAIndicator]
     * can display the icon.
     */
    private fun detectAndEmitNewEvents(matches: List<FootballMatch>) {
        val priority = listOf(
            EventType.RED_CARD,
            EventType.YELLOW_RED_CARD,
            EventType.GOAL,
            EventType.YELLOW_CARD,
        )
        var best: MatchEvent? = null
        var bestPriority = Int.MAX_VALUE

        for (match in matches) {
            for (event in match.events) {
                if (event.id in seenEventIds) continue
                seenEventIds += event.id
                val p = priority.indexOf(event.type).let { if (it < 0) 99 else it }
                if (p < bestPriority) { best = event; bestPriority = p }
            }
        }

        if (best != null) {
            android.util.Log.d(TAG, "Flash event: ${best.type} — ${best.playerName} (${best.teamName})")
            _flashEventFlow.value = FlashEvent(type = best.type)
        }
    }

    // =========================================================================
    //  Image backfill (TheSportsDB) — eager, primary logo source
    // =========================================================================

    /**
     * TheSportsDB is now the PREFERRED logo source (see [mergeMatches]), not a
     * last resort. So this fires a lookup for EVERY team in today's matches —
     * not just ones where FD/OLDB/AF came back null — so TSDB's better-quality,
     * properly-sized badge can override whatever the other three provided as
     * soon as it resolves.
     *
     * [backfillInFlight] deduplicates against lookups already in progress for
     * the same team, so a team appearing in match data on every poll (every
     * 15 s inside an AF window) doesn't spawn a new coroutine and queue up
     * behind [TheSportsDbSource]'s 30 s throttle on every single tick — once
     * a team is queued, repeat polls skip it until that lookup completes
     * (success, miss, or already-cached all clear the in-flight marker).
     *
     * Fire-and-forget: the lookup populates [TheSportsDbSource]'s permanent
     * cache, and [mergeMatches] reads that cache on every subsequent merge —
     * so as soon as a badge resolves, the next poll from ANY source (FD,
     * OLDB, or AF) will surface it. We also proactively re-merge immediately
     * after a successful batch so the UI doesn't wait for the next poll.
     */
    private val backfillInFlight = mutableSetOf<String>()

    private fun triggerImageBackfill(matches: List<FootballMatch>) {
        val candidateTeams = matches.flatMap { listOf(it.homeTeam, it.awayTeam) }.distinct()

        // Skip teams already resolved (hit or confirmed miss) or already in flight.
        val toFetch = candidateTeams.filter { team ->
            sportsDb.cachedBadge(team) == null && team !in backfillInFlight
        }
        if (toFetch.isEmpty()) return

        backfillInFlight += toFetch
        scope.launch {
            try {
                for (team in toFetch) {
                    sportsDb.fetchTeamBadge(team)
                }
            } finally {
                backfillInFlight -= toFetch.toSet()
            }
            // Re-merge now so newly-resolved badges show up immediately,
            // rather than waiting for the next scheduled FD/OLDB/AF poll.
            publishMerged()
        }
    }

    // =========================================================================
    //  football-data.org — fetch + parse
    // =========================================================================

    private suspend fun fetchFromFootballData(): List<FootballMatch> {
        if (apiKey.isBlank()) return emptyList()

        // One request covers all 8 FD competitions simultaneously.
        val competitionCodes = Competitions.FD_ALL.joinToString(",") { it.fdCode }
        if (competitionCodes.isBlank()) return emptyList()

        val url = "$FD_BASE/matches" +
                "?competitions=$competitionCodes" +
                "&dateFrom=${todayUtcString()}" +
                "&dateTo=${tomorrowUtcString()}"

        val json = withContext(Dispatchers.IO) {
            sharedRequestLog.record(FootballApiRequestLog.SOURCE_FOOTBALL_DATA_ORG)
            httpGet(url, headers = mapOf("X-Auth-Token" to apiKey))
        } ?: return emptyList()

        return try {
            parseFdMatches(JSONObject(json))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "FD parse error: $e")
            emptyList()
        }
    }

    private fun parseFdMatches(root: JSONObject): List<FootballMatch> {
        val matchesArr = root.optJSONArray("matches") ?: return emptyList()
        val compByCode = Competitions.FD_ALL.associateBy { it.fdCode }
        val result     = mutableListOf<FootballMatch>()

        for (i in 0 until matchesArr.length()) {
            val m      = matchesArr.getJSONObject(i)
            val fdCode = m.optJSONObject("competition")?.optString("code") ?: continue
            val comp   = compByCode[fdCode] ?: continue

            val status  = parseFdStatus(m.optString("status"))
            if (status == MatchStatus.UNKNOWN) continue

            val kickoff     = parseFdDate(m.optString("utcDate")) ?: continue
            val homeTeamObj = m.optJSONObject("homeTeam") ?: continue
            val awayTeamObj = m.optJSONObject("awayTeam") ?: continue
            val scoreObj    = m.optJSONObject("score")
            val fullTime    = scoreObj?.optJSONObject("fullTime")
            val halfTime    = scoreObj?.optJSONObject("halfTime")
            val liveScore   = scoreObj?.optJSONObject("regularTime") ?: fullTime

            val venue    = m.optString("venue").takeIf { it.isNotBlank() }
            val matchday = m.optInt("matchday", -1).takeIf { it > 0 }

            val (homeScore, awayScore) = when (status) {
                MatchStatus.FINISHED  -> Pair(fullTime?.optInt("home"), fullTime?.optInt("away"))
                MatchStatus.HALF_TIME -> Pair(halfTime?.optInt("home"), halfTime?.optInt("away"))
                MatchStatus.LIVE      -> Pair(liveScore?.optInt("home"), liveScore?.optInt("away"))
                else                  -> Pair(null, null)
            }

            // FD free plan does not expose per-match events in /v4/matches.
            // Events come from OLDB (goals) and AF (goals + cards).
            result += FootballMatch(
                id          = "fd_${m.optInt("id", 0)}",
                competition = comp,
                homeTeam    = homeTeamObj.optString("shortName").ifBlank { homeTeamObj.optString("name") },
                awayTeam    = awayTeamObj.optString("shortName").ifBlank { awayTeamObj.optString("name") },
                homeLogoUrl = homeTeamObj.optString("crest").takeIf { it.isNotBlank() },
                awayLogoUrl = awayTeamObj.optString("crest").takeIf { it.isNotBlank() },
                homeScore   = homeScore,
                awayScore   = awayScore,
                minute      = if (status == MatchStatus.LIVE) estimatedMinute(kickoff) else null,
                status      = status,
                kickoffMs   = kickoff,
                events      = emptyList(),
                sourceTag   = "football-data.org",
                venue       = venue,
                matchday    = matchday,
            )
        }

        return result
    }

    private fun parseFdStatus(s: String): MatchStatus = when (s.uppercase()) {
        "IN_PLAY"            -> MatchStatus.LIVE
        "PAUSED"             -> MatchStatus.HALF_TIME
        "FINISHED"           -> MatchStatus.FINISHED
        "TIMED", "SCHEDULED" -> MatchStatus.SCHEDULED
        else                 -> MatchStatus.UNKNOWN
    }

    // =========================================================================
    //  OpenLigaDB — fetch + parse
    // =========================================================================

    private suspend fun fetchFromOpenLigaDb(): List<FootballMatch> {
        val result = mutableListOf<FootballMatch>()

        for (comp in Competitions.OLDB_ALL) {
            // No matchday param → OpenLigaDB auto-serves the current matchday.
            val url  = "$OLDB_BASE/getmatchdata/${comp.oldbShortcut}/${comp.oldbSeason}"
            val json = withContext(Dispatchers.IO) {
                sharedRequestLog.record(FootballApiRequestLog.SOURCE_OPENLIGADB)
                httpGet(url)
            } ?: continue

            try {
                result += parseOldbMatches(JSONArray(json), comp)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "OLDB parse error [${comp.id}]: $e")
            }
        }

        return result
    }

    private fun parseOldbMatches(arr: JSONArray, comp: Competition): List<FootballMatch> {
        val today    = todayUtcString()
        val tomorrow = tomorrowUtcString()
        val result   = mutableListOf<FootballMatch>()

        for (i in 0 until arr.length()) {
            val m       = arr.getJSONObject(i)
            val kickoff = parseOldbDate(m.optString("matchDateTimeUTC")) ?: continue

            // Only keep today's and tomorrow's matches.
            val kickoffDate = utcDateString(kickoff)
            if (kickoffDate != today && kickoffDate != tomorrow) continue

            val isFinished = m.optBoolean("matchIsFinished", false)
            val team1      = m.optJSONObject("team1") ?: continue
            val team2      = m.optJSONObject("team2") ?: continue

            // Parse half-time and full-time results.
            val resultsArr = m.optJSONArray("matchResults")
            var htHome: Int? = null; var htAway: Int? = null
            var ftHome: Int? = null; var ftAway: Int? = null

            if (resultsArr != null) {
                for (r in 0 until resultsArr.length()) {
                    val res = resultsArr.getJSONObject(r)
                    when (res.optInt("resultTypeID")) {
                        1 -> { htHome = res.optInt("pointsTeam1"); htAway = res.optInt("pointsTeam2") }
                        2 -> { ftHome = res.optInt("pointsTeam1"); ftAway = res.optInt("pointsTeam2") }
                    }
                }
            }

            val now       = System.currentTimeMillis()
            val elapsedMs = now - kickoff

            val status = when {
                isFinished                                                      -> MatchStatus.FINISHED
                elapsedMs < 0                                                   -> MatchStatus.SCHEDULED
                elapsedMs in (45 * 60_000L)..(60 * 60_000L) && htHome != null  -> MatchStatus.HALF_TIME
                elapsedMs in 0..(105 * 60_000L)                                 -> MatchStatus.LIVE
                else                                                             -> MatchStatus.FINISHED
            }

            val (homeScore, awayScore) = when (status) {
                MatchStatus.FINISHED  -> Pair(ftHome, ftAway)
                MatchStatus.HALF_TIME -> Pair(htHome, htAway)
                MatchStatus.LIVE      -> Pair(ftHome ?: htHome, ftAway ?: htAway)
                else                  -> Pair(null, null)
            }

            val team1Name = team1.optString("shortName").ifBlank { team1.optString("teamName") }
            val team2Name = team2.optString("shortName").ifBlank { team2.optString("teamName") }

            val events = parseOldbGoals(
                goalsArr  = m.optJSONArray("goals"),
                team1Name = team1Name,
                team2Name = team2Name,
                matchId   = m.optInt("matchID", 0).toString(),
            )

            result += FootballMatch(
                id          = "oldb_${m.optInt("matchID", 0)}",
                competition = comp,
                homeTeam    = team1Name,
                awayTeam    = team2Name,
                homeLogoUrl = team1.optString("teamIconUrl").takeIf { it.isNotBlank() },
                awayLogoUrl = team2.optString("teamIconUrl").takeIf { it.isNotBlank() },
                homeScore   = homeScore,
                awayScore   = awayScore,
                minute      = if (status == MatchStatus.LIVE) estimatedMinute(kickoff) else null,
                status      = status,
                kickoffMs   = kickoff,
                events      = events,
                sourceTag   = "openligadb",
            )
        }

        return result
    }

    /**
     * OpenLigaDB only exposes goals, not cards or substitutions.
     * Goals are inferred from score progression in the "goals" array.
     */
    private fun parseOldbGoals(
        goalsArr: JSONArray?,
        team1Name: String,
        team2Name: String,
        matchId: String,
    ): List<MatchEvent> {
        if (goalsArr == null) return emptyList()
        val result = mutableListOf<MatchEvent>()

        for (i in 0 until goalsArr.length()) {
            val g       = goalsArr.getJSONObject(i)
            val goalId  = g.optInt("goalID", 0)
            val scorer  = g.optString("goalScorer").takeIf { it.isNotBlank() }
            val minute  = g.optInt("matchMinute", 0).takeIf { it > 0 }

            // Determine which team scored from cumulative score at goal time.
            val s1 = g.optInt("scoreTeam1", 0)
            val s2 = g.optInt("scoreTeam2", 0)
            val teamName = if (s1 > s2) team1Name else team2Name  // whichever team just went ahead

            result += MatchEvent(
                id         = "oldb_goal_${matchId}_$goalId",
                type       = EventType.GOAL,
                minute     = minute,
                playerName = scorer,
                teamName   = teamName,
            )
        }

        return result
    }

    // =========================================================================
    //  HTTP
    // =========================================================================

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout    = 8_000
            conn.requestMethod  = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                android.util.Log.e(TAG, "HTTP $code for $url — $err")
                return null
            }
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "httpGet exception [$url]: $e")
            null
        }
    }

    // =========================================================================
    //  Date helpers
    // =========================================================================

    private fun todayUtcString(): String    = utcDateString(System.currentTimeMillis())
    private fun tomorrowUtcString(): String = utcDateString(System.currentTimeMillis() + 86_400_000L)

    private fun utcDateString(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(epochMs))
    }

    private fun parseFdDate(s: String): Long? =
        if (s.isBlank()) null
        else try { ISO_FMT_OFFSET.parse(s)?.time }
        catch (_: Exception) {
            try { ISO_FMT.parse(s)?.time } catch (_: Exception) { null }
        }

    private fun parseOldbDate(s: String): Long? = parseFdDate(s)

    /** Rough live-minute estimate when the source doesn't provide one. */
    private fun estimatedMinute(kickoffMs: Long): Int =
        ((System.currentTimeMillis() - kickoffMs) / 60_000L).toInt().coerceIn(1, 90)
}