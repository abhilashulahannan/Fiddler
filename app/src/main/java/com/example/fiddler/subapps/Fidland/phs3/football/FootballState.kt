package com.example.fiddler.subapps.Fidland.phs3.football

// ─────────────────────────────────────────────────────────────────────────────
//  Competition registry
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Which API is the primary source for this competition's schedule data.
 *
 * FOOTBALL_DATA_ORG  — X-Auth-Token header required. Free plan: 10 req/min,
 *   ~5 min live delay, limited per-match detail. Used as schedule-of-record
 *   for all 8 tracked competitions.
 *
 * OPEN_LIGA_DB       — Free, no key, ~1 min update cadence. Covers Bundesliga
 *   (and other German-adjacent leagues) with goals and basic event data.
 *   Used as a live-data overlay/gap-filler alongside football-data.org.
 */
enum class FootballSource { FOOTBALL_DATA_ORG, OPEN_LIGA_DB }

data class Competition(
    val id: String,              // internal key, e.g. "PL"
    val displayName: String,     // shown in State 5 header
    val source: FootballSource,

    /** football-data.org competition code. Only meaningful for FOOTBALL_DATA_ORG. */
    val fdCode: String = "",

    /** OpenLigaDB league shortcut, e.g. "bl1". Only meaningful for OPEN_LIGA_DB. */
    val oldbShortcut: String = "",

    /** OpenLigaDB season start year, e.g. 2025 for the 2025/26 season. */
    val oldbSeason: Int = 0,
)

object Competitions {

    // ── football-data.org — schedule of record ───────────────────────────────
    // All 8 competitions listed here are covered by the free FD plan.
    // FD is polled once per minute (well under the 10/min cap) to keep the
    // day's schedule and active-window plan up to date.

    val WORLD_CUP = Competition(
        id = "WC", displayName = "World Cup",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "WC",
    )
    val EUROS = Competition(
        id = "EC", displayName = "Euro Championship",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "EC",
    )
    val UCL = Competition(
        id = "CL", displayName = "Champions League",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "CL",
    )
    val PREMIER_LEAGUE = Competition(
        id = "PL", displayName = "Premier League",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "PL",
    )
    val PRIMERA_DIVISION = Competition(
        id = "PD", displayName = "La Liga",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "PD",
    )
    val SERIE_A = Competition(
        id = "SA", displayName = "Serie A",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "SA",
    )
    val LIGUE_1 = Competition(
        id = "FL1", displayName = "Ligue 1",
        source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "FL1",
    )

    // ── OpenLigaDB — live overlay for Bundesliga ─────────────────────────────
    // Bundesliga is also tracked via FD (fdCode = "BL1") but OpenLigaDB
    // provides near-real-time goals so OLDB supplements the FD data here.
    // The app merges both, with OLDB winning on live detail for this league.

    val BUNDESLIGA = Competition(
        id = "BL1", displayName = "Bundesliga",
        source = FootballSource.OPEN_LIGA_DB,
        oldbShortcut = "bl1",
        oldbSeason = 2025,           // 2025/26 season; bump each summer
    )

    // ── Master lists ──────────────────────────────────────────────────────────

    /**
     * All 8 competitions tracked by this module.
     * Reorder to change priority in the merge / UI display.
     */
    val ALL = listOf(
        WORLD_CUP,
        EUROS,
        UCL,
        PREMIER_LEAGUE,
        PRIMERA_DIVISION,
        SERIE_A,
        BUNDESLIGA,
        LIGUE_1,
    )

    /**
     * Competitions polled via football-data.org (schedule + status baseline).
     * Note: BUNDESLIGA appears here too — FD gives its schedule even though
     * OLDB provides its live detail.
     */
    val FD_ALL get() = listOf(
        WORLD_CUP, EUROS, UCL,
        PREMIER_LEAGUE, PRIMERA_DIVISION, SERIE_A, LIGUE_1,
        // BL1 has a football-data.org code too; include it so FD's schedule
        // covers Bundesliga kickoff times for the active-window engine.
        Competition(
            id = "BL1_FD", displayName = "Bundesliga",
            source = FootballSource.FOOTBALL_DATA_ORG, fdCode = "BL1",
        ),
    )

    /** Competitions polled via OpenLigaDB (live detail, near-real-time). */
    val OLDB_ALL get() = listOf(BUNDESLIGA)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Match model
// ─────────────────────────────────────────────────────────────────────────────

enum class MatchStatus {
    SCHEDULED,    // not yet started
    LIVE,         // actively in play
    HALF_TIME,    // between halves
    FINISHED,     // full-time
    UNKNOWN,
}

/**
 * A single match snapshot, potentially assembled from multiple sources.
 *
 * The [sourceTag] field records which API last wrote the live fields
 * (status / score / minute / events). It is informational — used for
 * debug logging and tie-breaking in [FootballRepository.mergeOnto].
 *
 * [fixtureKey] produces a source-independent key so the same real-world
 * fixture from FD, OLDB, and AF can be collapsed into one object during
 * the merge step.
 *
 * @param id            Unique match ID (prefixed with source, e.g. "fd_123").
 * @param competition   Which competition this match belongs to.
 * @param homeTeam      Home team short name.
 * @param awayTeam      Away team short name.
 * @param homeLogoUrl   Home crest URL (may come from any source, or TSDB backfill).
 * @param awayLogoUrl   Away crest URL.
 * @param homeScore     Goals by home team; null if match not yet started.
 * @param awayScore     Goals by away team; null if match not yet started.
 * @param minute        Current match minute (1–90+), or null.
 * @param status        Live / Finished / Scheduled etc.
 * @param kickoffMs     Epoch ms of scheduled kick-off (UTC).
 * @param events        Ordered list of in-match events (goals, cards…).
 * @param sourceTag     Which API last provided live data for this match.
 * @param venue         Stadium / venue name, or null if not provided by any source.
 *                      Currently only football-data.org exposes this field.
 * @param matchday      Competition matchday / round number, or null if not applicable
 *                      (e.g. knockout-stage fixtures without a numbered matchday).
 *                      Currently only football-data.org exposes this field.
 */
data class FootballMatch(
    val id: String,
    val competition: Competition,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogoUrl: String?,
    val awayLogoUrl: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val minute: Int?,
    val status: MatchStatus,
    val kickoffMs: Long,
    val events: List<MatchEvent> = emptyList(),
    val sourceTag: String = "",      // "football-data.org" | "openligadb" | "api-football"
    val venue: String? = null,
    val matchday: Int? = null,
) {
    /**
     * Source-independent fixture key used to deduplicate across APIs.
     *
     * We cannot rely on numeric IDs (each API assigns its own). Instead we
     * key on {normalised home name} + {normalised away name} + {kick-off day}.
     * This is resilient to minor name variations but assumes the same match
     * doesn't appear twice on the same day between the same two teams
     * (a reasonable assumption for top-flight football).
     */
    fun fixtureKey(): String {
        val date   = utcDateOf(kickoffMs)
        val home   = homeTeam.lowercase().replace(Regex("[^a-z0-9]"), "")
        val away   = awayTeam.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${date}_${home}_${away}"
    }

    companion object {
        private fun utcDateOf(epochMs: Long): String {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return fmt.format(java.util.Date(epochMs))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Event model
// ─────────────────────────────────────────────────────────────────────────────

enum class EventType {
    GOAL,
    YELLOW_CARD,
    RED_CARD,
    YELLOW_RED_CARD,   // second yellow → red
    SUBSTITUTION,
    OTHER,
}

/**
 * A single match event (goal, card, etc.).
 *
 * @param id          Unique event ID — used to detect new events between polls
 *                    without re-firing flash notifications for old ones.
 * @param type        What kind of event.
 * @param minute      Minute of event (e.g. 45), or null if unknown.
 * @param playerName  Player involved, or null.
 * @param teamName    Team the player belongs to.
 */
data class MatchEvent(
    val id: String,
    val type: EventType,
    val minute: Int?,
    val playerName: String?,
    val teamName: String,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Flash-event model (location a)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Emitted into [FootballRepository.flashEventFlow] when a NEW event is
 * detected. The handler in [FootballPhs3Handler.LocationAIndicator] shows
 * the icon for [FLASH_DURATION_MS] then hides it automatically.
 */
data class FlashEvent(
    val type: EventType,
    val arrivedAtMs: Long = System.currentTimeMillis(),
)

/** How long the location-a icon stays visible after a new event is detected. */
const val FLASH_DURATION_MS: Long = 30_000L

// ─────────────────────────────────────────────────────────────────────────────
//  Convenience extensions
// ─────────────────────────────────────────────────────────────────────────────

/** Score string shown in location b — e.g. "2 : 1". Null scores shown as "-". */
fun FootballMatch.scoreLabel(): String {
    val h = homeScore?.toString() ?: "-"
    val a = awayScore?.toString() ?: "-"
    return "$h : $a"
}

/** True while this match is actively playing (includes half-time break). */
fun FootballMatch.isActive(): Boolean =
    status == MatchStatus.LIVE || status == MatchStatus.HALF_TIME

/** Kick-off time formatted as "HH:MM" in device local time. */
fun FootballMatch.kickoffTime(): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = kickoffMs
    return "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
}

/** Status label shown in State 5. */
fun FootballMatch.statusLabel(): String = when (status) {
    MatchStatus.LIVE      -> if (minute != null) "${minute}'" else "LIVE"
    MatchStatus.HALF_TIME -> "Half Time"
    MatchStatus.FINISHED  -> "Full Time"
    MatchStatus.SCHEDULED -> kickoffTime()
    MatchStatus.UNKNOWN   -> ""
}

/**
 * Matchday label shown in State 5 detail view, e.g. "Matchday 12".
 * Returns null if no matchday number is known (knockout legs without one,
 * or a source that doesn't provide it).
 */
fun FootballMatch.matchdayLabel(): String? =
    matchday?.let { "Matchday $it" }