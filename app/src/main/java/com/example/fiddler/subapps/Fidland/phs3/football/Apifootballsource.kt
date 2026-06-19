package com.example.fiddler.subapps.Fidland.phs3.football

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * ApiFootballSource — fetches live fixture data from api-football (api-sports.io).
 *
 * ── Free-tier constraints ─────────────────────────────────────────────────────
 *   • 100 requests / day (resets 00:00 UTC). Hard cap enforced here AND by
 *     [AfRequestLog] independently, so a reconcile bug can never burn the budget.
 *   • One call to /fixtures?live=all covers ALL leagues simultaneously.
 *   • No documented per-minute rate limit, but back-to-back calls within ~30 s
 *     can trigger 429s. [AfRequestLog] enforces [MIN_SPACING_MS] (90 s) between
 *     calls as the primary guard.
 *
 * ── Request log ───────────────────────────────────────────────────────────────
 *   Every call attempt is checked against [AfRequestLog] BEFORE the HTTP
 *   request is made. The log persists to disk (7-day rolling window) so the
 *   guard survives app restarts. [AfRequestLog.record] is called as soon as the
 *   request is dispatched — not after parsing — so failed calls still count.
 *
 * ── Polling intervals ─────────────────────────────────────────────────────────
 *   [FootballRepository.startAfLoop] uses [nextAfCheckDelayMs] to decide how
 *   long to sleep between plan-check iterations:
 *
 *   | Situation                              | Check interval |
 *   |----------------------------------------|----------------|
 *   | Inside active match window             |       15 s     |  ← tight; next planned poll may be soon
 *   | Within 30 min of a window opening      |       60 s     |  ← wake up before the window
 *   | No window active or imminent today     |    5–15 min    |  ← nothing to do; conserve battery
 *
 *   Within the check loop, the ACTUAL api-football HTTP call only fires when a
 *   [FootballScheduleEngine.PlannedPoll] timestamp is due AND [AfRequestLog.canFire]
 *   gives the green light. Inside windows polls are spaced ~every few minutes by
 *   the schedule engine; outside windows they're spaced 5–15 min apart (via the
 *   plan's allotment distribution), subject to the 90 s floor.
 *
 * ── Daily counter ─────────────────────────────────────────────────────────────
 *   [callsUsedToday] is derived live from [AfRequestLog.todayCount] so it
 *   survives process restarts without a manual reset call.
 *
 * @param context  Application / service context (for [AfRequestLog] file I/O).
 * @param apiKey   Your api-sports.io free API key. Pass blank to disable.
 */
class ApiFootballSource(
    private val context: Context,
    private val apiKey: String,
) {

    companion object {
        private const val TAG  = "ApiFootballSource"
        private const val BASE = "https://v3.football.api-sports.io"

        /**
         * Minimum gap between any two api-football HTTP calls.
         * Enforced by [AfRequestLog] regardless of what the schedule engine says.
         * 90 s gives comfortable headroom above any undocumented per-minute limit.
         */
        const val MIN_SPACING_MS: Long = 90_000L

        /**
         * Hard daily cap — kept at 100 to match the api-football free tier exactly.
         * [AfRequestLog] enforces this independently of the schedule engine.
         */
        const val DAILY_CAP: Int = 100

        // ── AF check-loop sleep durations (used by FootballRepository) ────────

        /** Inside an active window: check the plan frequently. */
        const val CHECK_INTERVAL_IN_WINDOW_MS:  Long =  15_000L  //  15 s

        /** Approaching a window (within 30 min): wake up before it opens. */
        const val CHECK_INTERVAL_PRE_WINDOW_MS: Long =  60_000L  //   1 min

        /** No active or imminent window: sleep long; nothing to fire. */
        const val CHECK_INTERVAL_IDLE_MS:       Long = 10 * 60_000L  // 10 min

        // ── Within-plan poll spacing (inside vs outside windows) ──────────────
        // These are targets baked into FootballScheduleEngine's allotment maths.
        // Outside active windows the plan spaces polls 5–15 min apart; inside
        // windows it spaces them every few minutes depending on window duration
        // and remaining budget. The 90 s floor in AfRequestLog is the backstop.

        // api-football league IDs for our 8 tracked competitions.
        private val TRACKED_LEAGUE_IDS = setOf(
            1,    // World Cup
            4,    // Euro Championship
            2,    // Champions League
            39,   // Premier League
            140,  // La Liga
            135,  // Serie A
            78,   // Bundesliga
            61,   // Ligue 1
        )

        private val LEAGUE_ID_TO_COMPETITION = mapOf(
            1   to Competitions.WORLD_CUP,
            4   to Competitions.EUROS,
            2   to Competitions.UCL,
            39  to Competitions.PREMIER_LEAGUE,
            140 to Competitions.PRIMERA_DIVISION,
            135 to Competitions.SERIE_A,
            78  to Competitions.BUNDESLIGA,
            61  to Competitions.LIGUE_1,
        )
    }

    // ── Request log ───────────────────────────────────────────────────────────

    /**
     * Persistent 7-day rolling log of every api-football call.
     * Exposed so [FootballRepository] can dump [AfRequestLog.frequencySummary]
     * to logcat for debugging without exposing the full log internals.
     */
    val requestLog: AfRequestLog = AfRequestLog(
        context      = context,
        minSpacingMs = MIN_SPACING_MS,
        dailyCap     = DAILY_CAP,
    )

    /**
     * Shared, cross-source counter used only for the "requests sent today"
     * display in the Football settings screen. Does not gate calls — [AfRequestLog]
     * above remains the sole enforcement point for the 100/day budget.
     */
    private val sharedRequestLog: FootballApiRequestLog = FootballApiRequestLog(context)

    // ── Derived counter (survives restarts via the persistent log) ────────────

    /** How many calls have been recorded today (UTC). Read from [AfRequestLog]. */
    val callsUsedToday: Int get() = requestLog.todayCount()

    // ── Live fixture fetch ────────────────────────────────────────────────────

    /**
     * Attempt to fetch all currently live fixtures.
     *
     * Pre-flight sequence (all on Dispatchers.IO):
     *   1. Blank key check  — instant reject.
     *   2. [AfRequestLog.canFire] — enforces daily cap AND 90 s minimum spacing.
     *      Logs the reason either way. If blocked → return null immediately.
     *   3. [AfRequestLog.record] — written BEFORE the HTTP call so the slot is
     *      consumed even if the network fails, preventing retry storms.
     *   4. HTTP GET /fixtures?live=all.
     *   5. Parse and return.
     *
     * @param inWindow  Pass true when this call is being fired inside an active
     *                  match window; recorded in the log for analysis.
     */
    suspend fun fetchLiveFixtures(inWindow: Boolean = false): List<FootballMatch>? {
        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()

            // ── Pre-flight: log check ─────────────────────────────────────────
            val check = requestLog.canFire(nowMs)
            Log.d(TAG, "AF pre-flight: ${check.reason}")

            if (!check.allowed) {
                // Dump frequency summary whenever a call is blocked so it's easy
                // to see the recent pattern in logcat.
                Log.w(TAG, "AF call BLOCKED — frequency summary:\n${requestLog.frequencySummary(nowMs)}")
                return@withContext null
            }

            // ── Consume the slot before the HTTP round-trip ───────────────────
            requestLog.record(nowMs = nowMs, inWindow = inWindow)
            sharedRequestLog.record(source = FootballApiRequestLog.SOURCE_API_FOOTBALL, nowMs = nowMs)
            Log.d(TAG, "AF firing call #${requestLog.todayCount()} today (inWindow=$inWindow)")

            // ── HTTP ──────────────────────────────────────────────────────────
            val json = httpGet(
                url     = "$BASE/fixtures?live=all",
                headers = mapOf("x-apisports-key" to apiKey),
            ) ?: return@withContext null

            // ── Parse ─────────────────────────────────────────────────────────
            try {
                parseLiveFixtures(JSONObject(json))
            } catch (e: Exception) {
                Log.e(TAG, "AF parse error: $e")
                null
            }
        }
    }

    /**
     * Context-aware sleep duration for the AF check loop in [FootballRepository].
     *
     * Called by [FootballRepository.startAfLoop] after each plan-check iteration
     * to decide how long to sleep before checking again.
     *
     * | Situation                                  | Sleep      |
     * |--------------------------------------------|------------|
     * | Inside an active match window              |    15 s    |
     * | Within 30 min of a window starting         |     1 min  |
     * | No active/imminent window (idle)           |    10 min  |
     *
     * Within active windows the schedule engine places planned polls every few
     * minutes; the 15 s check interval ensures we catch each one promptly.
     * Outside windows there's nothing to fire for a while, so sleeping 10 min
     * avoids unnecessary wake-ups.
     */
    fun nextCheckDelayMs(plan: FootballScheduleEngine.DailyPollPlan?): Long {
        if (plan == null) return CHECK_INTERVAL_IDLE_MS
        val now = System.currentTimeMillis()

        if (FootballScheduleEngine.isInActiveWindow(plan, now)) return CHECK_INTERVAL_IN_WINDOW_MS

        val nextWindowStart = plan.windows
            .map { it.startMs }
            .filter { it > now }
            .minOrNull() ?: return CHECK_INTERVAL_IDLE_MS

        val timeUntil = nextWindowStart - now
        return if (timeUntil <= 30 * 60_000L) CHECK_INTERVAL_PRE_WINDOW_MS
        else                            CHECK_INTERVAL_IDLE_MS
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseLiveFixtures(root: JSONObject): List<FootballMatch> {
        val responseArr = root.optJSONArray("response") ?: return emptyList()
        val result = mutableListOf<FootballMatch>()

        for (i in 0 until responseArr.length()) {
            val entry = responseArr.getJSONObject(i)

            val leagueId = entry.optJSONObject("league")?.optInt("id", -1) ?: -1
            if (leagueId !in TRACKED_LEAGUE_IDS) continue
            val competition = LEAGUE_ID_TO_COMPETITION[leagueId] ?: continue

            val fixtureObj  = entry.optJSONObject("fixture") ?: continue
            val statusObj   = fixtureObj.optJSONObject("status")
            val statusShort = statusObj?.optString("short") ?: ""
            val elapsed     = statusObj?.optInt("elapsed", -1)?.takeIf { it >= 0 }

            val status = parseAfStatus(statusShort)
            if (status == MatchStatus.UNKNOWN) continue

            val teamsObj = entry.optJSONObject("teams") ?: continue
            val homeObj  = teamsObj.optJSONObject("home") ?: continue
            val awayObj  = teamsObj.optJSONObject("away") ?: continue
            val goalsObj = entry.optJSONObject("goals")

            val events = parseAfEvents(
                entry.optJSONArray("events"),
                matchId = fixtureObj.optInt("id", 0).toString(),
            )

            val nowMs = System.currentTimeMillis()
            val approximateKickoffMs = if (elapsed != null && elapsed > 0)
                nowMs - elapsed * 60_000L else nowMs

            result += FootballMatch(
                id          = "af_${fixtureObj.optInt("id", 0)}",
                competition = competition,
                homeTeam    = homeObj.optString("name"),
                awayTeam    = awayObj.optString("name"),
                homeLogoUrl = homeObj.optString("logo").takeIf { it.isNotBlank() },
                awayLogoUrl = awayObj.optString("logo").takeIf { it.isNotBlank() },
                homeScore   = goalsObj?.optInt("home")?.takeIf { it >= 0 },
                awayScore   = goalsObj?.optInt("away")?.takeIf { it >= 0 },
                minute      = elapsed,
                status      = status,
                kickoffMs   = approximateKickoffMs,
                events      = events,
                sourceTag   = "api-football",
            )
        }

        return result
    }

    private fun parseAfStatus(short: String): MatchStatus = when (short.uppercase()) {
        "1H", "2H", "ET", "P"  -> MatchStatus.LIVE
        "HT"                    -> MatchStatus.HALF_TIME
        "FT", "AET", "PEN"     -> MatchStatus.FINISHED
        "NS", "TBD"             -> MatchStatus.SCHEDULED
        else                    -> MatchStatus.UNKNOWN
    }

    private fun parseAfEvents(
        eventsArr: org.json.JSONArray?,
        matchId: String,
    ): List<MatchEvent> {
        if (eventsArr == null) return emptyList()
        val result = mutableListOf<MatchEvent>()

        for (i in 0 until eventsArr.length()) {
            val ev       = eventsArr.getJSONObject(i)
            val typeStr  = ev.optString("type").uppercase()
            val detail   = ev.optString("detail").uppercase()
            val elapsed  = ev.optJSONObject("time")?.optInt("elapsed")?.takeIf { it > 0 }
            val player   = ev.optJSONObject("player")?.optString("name")?.takeIf { it.isNotBlank() }
            val teamName = ev.optJSONObject("team")?.optString("name") ?: ""

            val eventType = when {
                typeStr == "GOAL"                                         -> EventType.GOAL
                typeStr == "CARD" && detail == "YELLOW CARD"             -> EventType.YELLOW_CARD
                typeStr == "CARD" && detail == "RED CARD"                -> EventType.RED_CARD
                typeStr == "CARD" && "SECOND YELLOW" in detail           -> EventType.YELLOW_RED_CARD
                typeStr == "SUBST"                                       -> EventType.SUBSTITUTION
                else                                                     -> EventType.OTHER
            }

            result += MatchEvent(
                id         = "af_ev_${matchId}_${i}",
                type       = eventType,
                minute     = elapsed,
                playerName = player,
                teamName   = teamName,
            )
        }

        return result
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun httpGet(url: String, headers: Map<String, String>): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.requestMethod  = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "HTTP $code for $url — $err")
                return null
            }
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "httpGet exception: $e")
            null
        }
    }
}