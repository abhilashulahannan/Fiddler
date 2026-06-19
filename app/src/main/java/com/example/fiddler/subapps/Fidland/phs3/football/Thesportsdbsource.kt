package com.example.fiddler.subapps.Fidland.phs3.football

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * TheSportsDbSource — on-demand team data backfill from thesportsdb.com.
 *
 * ── Role ─────────────────────────────────────────────────────────────────────
 *   TheSportsDB does not provide live scores. It is used as the PREFERRED
 *   source for team badge (crest) URLs — see [FootballRepository.mergeMatches]
 *   for the precedence rule. [FootballRepository.triggerImageBackfill] looks up
 *   every team in today's matches eagerly (not only ones missing a logo from
 *   the other sources), since TSDB's badges are properly sized (see "Image
 *   sizing" below) whereas FD/OLDB/AF each only expose a single fixed-size crest.
 *
 *   This version also extracts and caches a full [ClubProfile] per team from
 *   the SAME searchteams response — zero additional API calls. Fields harvested:
 *     • strBadge         → badge URL (existing)
 *     • intFormedYear    → [ClubProfile.founded]
 *     • strCountry       → [ClubProfile.country]
 *     • strStadium       → [ClubProfile.stadium]
 *     • intStadiumCapacity → [ClubProfile.stadiumCapacity]
 *     • strStadiumThumb  → [ClubProfile.stadiumThumbUrl]  ("/small" variant)
 *     • strStadiumLocation → [ClubProfile.stadiumLocation]
 *     • strDescriptionEN → [ClubProfile.description]
 *
 * ── Rate limit ────────────────────────────────────────────────────────────────
 *   The free v1 API (no key, no account) is rate-limited to roughly 1–2 req/s
 *   globally. We enforce a minimum [MIN_CALL_INTERVAL_MS] = 30 000 ms (30 s)
 *   between any two HTTP calls from this process to stay well within limits.
 *   We err very conservatively because backfill is non-urgent — a badge
 *   missing for 30 s is acceptable; a 429 ban is not.
 *
 * ── In-memory cache ───────────────────────────────────────────────────────────
 *   Results are stored in [profileCache] (normalised team name → [CacheEntry])
 *   for the lifetime of the process. Once a lookup is done (hit or miss) it
 *   is never re-fetched. This means the throttle only matters for teams seen
 *   for the first time this process lifetime.
 *
 * ── Endpoints used ────────────────────────────────────────────────────────────
 *   Team search (by name):
 *     https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t={teamName}
 *   Response (fields extracted):
 *     {
 *       "teams": [{
 *         "strTeam":             "Real Madrid",
 *         "strBadge":            "https://…/…/Real_Madrid.png",
 *         "intFormedYear":       "1902",
 *         "strCountry":          "Spain",
 *         "strStadium":          "Santiago Bernabéu",
 *         "intStadiumCapacity":  "81044",
 *         "strStadiumThumb":     "https://…/…/bernabeu.jpg",
 *         "strStadiumLocation":  "Madrid",
 *         "strDescriptionEN":    "Real Madrid CF is a professional…"
 *       }]
 *     }
 *   The free v1 API key is literally the string "3" — no registration needed.
 *
 * ── Image sizing ──────────────────────────────────────────────────────────────
 *   TheSportsDB serves badges AND stadium thumbs at multiple resolutions via a
 *   URL suffix: original (no suffix, ~720px), /medium (500px), /small (250px),
 *   /tiny (50px). This source standardises on [BADGE_SUFFIX] ("/small") for
 *   badge URLs and [STADIUM_SUFFIX] ("/small") for stadium thumbnails. Both are
 *   appended once here; everything downstream (cache, merge, UI) just consumes
 *   the stored URL — no caller needs to think about sizing.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 *   A [Mutex] ([fetchLock]) ensures only one network call is in-flight at a
 *   time, preserving the 30 s minimum gap across concurrent callers. All public
 *   methods are safe to call from multiple coroutines simultaneously.
 *
 * ── Backward compatibility ────────────────────────────────────────────────────
 *   [cachedBadge] and [fetchTeamBadge] preserve their exact original signatures
 *   — no call sites in [FootballRepository] need to change. The new
 *   [cachedClubProfile] method is additive.
 *
 * @param context Application / service context, used only to record each call
 *                into the shared [FootballApiRequestLog] for the "requests
 *                sent today" counter in the Football settings screen.
 */
class TheSportsDbSource(context: Context) {

    companion object {
        private const val TAG  = "TheSportsDbSource"
        private const val BASE = "https://www.thesportsdb.com/api/v1/json/3"

        /** Minimum gap between consecutive HTTP calls (30 s per design contract). */
        private const val MIN_CALL_INTERVAL_MS: Long = 30_000L

        /** URL suffix for badge images — "small" = 250 px. */
        private const val BADGE_SUFFIX = "/small"

        /** URL suffix for stadium thumbnail images — "small" = 250 px. */
        private const val STADIUM_SUFFIX = "/small"

        /**
         * Truncate [ClubProfile.description] to this many characters.
         * The full EN description from TSDB can be several paragraphs — we store
         * only the first sentence / opening clause for display in the Clubs tab.
         */
        private const val DESC_MAX_CHARS = 160
    }

    // ── Cache entry ───────────────────────────────────────────────────────────

    /**
     * Internal cache entry produced by a single searchteams call.
     *
     * [badgeUrl] null means TSDB returned no usable badge (either not found or
     * the field was blank). [profile] null means the same. Both can be non-null,
     * both can be null, independently.
     *
     * [resolved] = true means the network call has been made (successfully or
     * not) and this entry is final for the process lifetime.
     */
    private data class CacheEntry(
        val resolved: Boolean,
        val badgeUrl: String?,
        val profile: ClubProfile?,
    ) {
        companion object {
            /** Sentinel used while the async fetch is in-progress (not stored, just a guard). */
            val PENDING = CacheEntry(resolved = false, badgeUrl = null, profile = null)
            /** Stored when the API returned no results for this team. */
            val MISSING = CacheEntry(resolved = true,  badgeUrl = null, profile = null)
        }
    }

    /** Maps normalised team name → [CacheEntry]. */
    private val profileCache = mutableMapOf<String, CacheEntry>()

    private val sharedRequestLog = FootballApiRequestLog(context)

    // ── Throttle ──────────────────────────────────────────────────────────────

    private val fetchLock = Mutex()
    private var lastCallMs: Long = 0L

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Synchronous cache-only badge lookup — never suspends, never makes a network
     * call. Returns the sized badge URL if already resolved, null otherwise.
     *
     * Signature is identical to the original — no call sites need to change.
     */
    fun cachedBadge(teamName: String): String? =
        profileCache[normaliseName(teamName)]?.badgeUrl

    /**
     * Synchronous cache-only [ClubProfile] lookup — never suspends, never makes
     * a network call. Returns the resolved profile if the team has been fetched
     * this process lifetime, null if not yet fetched or not found on TSDB.
     *
     * Called by [FootballListView]'s [buildExtras] on the main thread during
     * recomposition — must never block.
     */
    fun cachedClubProfile(teamName: String): ClubProfile? =
        profileCache[normaliseName(teamName)]?.profile

    /**
     * Fetch badge URL for [teamName], caching the full [ClubProfile] as a
     * zero-cost side-effect from the same response.
     *
     * Behaviour is identical to the original [fetchTeamBadge]:
     *   • Returns immediately if cached (fast path).
     *   • Otherwise acquires [fetchLock], waits for the throttle gap, then
     *     calls the API. The result (badge URL or null) is returned, AND the
     *     full [ClubProfile] is stored in [profileCache] at no extra cost.
     *
     * Signature is identical to the original — no call sites need to change.
     */
    suspend fun fetchTeamBadge(teamName: String): String? {
        val key = normaliseName(teamName)

        // Fast path — already resolved.
        profileCache[key]?.takeIf { it.resolved }?.let { return it.badgeUrl }

        // Slow path — fetch from network, serialised through lock + throttle.
        return fetchLock.withLock {
            // Re-check inside the lock (another coroutine may have populated it).
            profileCache[key]?.takeIf { it.resolved }?.let { return@withLock it.badgeUrl }

            enforceThrottle()

            val entry = fetchAndParse(teamName)
            profileCache[key] = entry
            entry.badgeUrl
        }
    }

    // ── Internal — throttle ───────────────────────────────────────────────────

    /** Must be called inside [fetchLock]. Delays if the 30 s gap hasn't elapsed. */
    private suspend fun enforceThrottle() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCallMs
        if (elapsed < MIN_CALL_INTERVAL_MS) {
            val waitMs = MIN_CALL_INTERVAL_MS - elapsed
            android.util.Log.d(TAG, "Throttle: waiting ${waitMs}ms before next TSDB call")
            kotlinx.coroutines.delay(waitMs)
        }
        lastCallMs = System.currentTimeMillis()
    }

    // ── Internal — fetch & parse ──────────────────────────────────────────────

    /**
     * Fires one HTTP call to searchteams, parses the response into a
     * [CacheEntry] containing both the badge URL and the full [ClubProfile].
     * Returns [CacheEntry.MISSING] on network error or no results.
     */
    private suspend fun fetchAndParse(teamName: String): CacheEntry {
        val encoded = java.net.URLEncoder.encode(teamName, "UTF-8")
        val url = "$BASE/searchteams.php?t=$encoded"

        sharedRequestLog.record(FootballApiRequestLog.SOURCE_THESPORTSDB, nowMs = lastCallMs)

        val json = withContext(Dispatchers.IO) { httpGet(url) }
            ?: return CacheEntry.MISSING

        return try {
            val root  = JSONObject(json)
            val teams = root.optJSONArray("teams")

            if (teams == null || teams.length() == 0) {
                android.util.Log.d(TAG, "TSDB: no team found for '$teamName'")
                return CacheEntry.MISSING
            }

            // Prefer an exact name match; fall back to the first result.
            var best: JSONObject? = null
            for (i in 0 until teams.length()) {
                val t = teams.getJSONObject(i)
                if (t.optString("strTeam").equals(teamName, ignoreCase = true)) {
                    best = t
                    break
                }
                if (best == null) best = t
            }

            if (best == null) return CacheEntry.MISSING

            val entry = parseCacheEntry(teamName, best)
            android.util.Log.d(TAG, "TSDB: resolved '$teamName' → badge=${entry.badgeUrl}, stadium=${entry.profile?.stadium}")
            entry

        } catch (e: Exception) {
            android.util.Log.e(TAG, "TSDB parse error for '$teamName': $e")
            CacheEntry.MISSING
        }
    }

    /**
     * Extracts all relevant fields from a single team JSON object and builds
     * a [CacheEntry]. Every field is parsed here — only one place to update
     * when TSDB adds new response fields.
     */
    private fun parseCacheEntry(teamName: String, t: JSONObject): CacheEntry {

        // ── Badge ─────────────────────────────────────────────────────────────
        val badgeUrl = t.optString("strBadge")
            .takeIf { it.isNotBlank() }
            ?.plus(BADGE_SUFFIX)

        // ── Founded year ──────────────────────────────────────────────────────
        // intFormedYear is a string in TSDB's response (e.g. "1902").
        val founded = t.optString("intFormedYear")
            .trim()
            .toIntOrNull()

        // ── Country ───────────────────────────────────────────────────────────
        val country = t.optString("strCountry").takeIf { it.isNotBlank() }

        // ── Stadium ───────────────────────────────────────────────────────────
        val stadium = t.optString("strStadium").takeIf { it.isNotBlank() }

        // intStadiumCapacity is also a string in TSDB (e.g. "81044").
        val stadiumCapacity = t.optString("intStadiumCapacity")
            .trim()
            .toIntOrNull()

        // Stadium thumbnail — sized to /small (250 px) for venue tab display.
        val stadiumThumbUrl = t.optString("strStadiumThumb")
            .takeIf { it.isNotBlank() }
            ?.plus(STADIUM_SUFFIX)

        val stadiumLocation = t.optString("strStadiumLocation").takeIf { it.isNotBlank() }

        // ── Description ───────────────────────────────────────────────────────
        // Truncate to first sentence / DESC_MAX_CHARS to avoid walls of text
        // in the Clubs tab. A null description is fine — the card omits the field.
        val rawDesc = t.optString("strDescriptionEN").takeIf { it.isNotBlank() }
        val description = rawDesc?.let { truncateDescription(it) }

        // ── Assemble ──────────────────────────────────────────────────────────
        val profile = ClubProfile(
            teamName         = teamName,
            founded          = founded,
            country          = country,
            stadium          = stadium,
            stadiumCapacity  = stadiumCapacity,
            stadiumThumbUrl  = stadiumThumbUrl,
            stadiumLocation  = stadiumLocation,
            description      = description,
        )

        return CacheEntry(
            resolved = true,
            badgeUrl = badgeUrl,
            profile  = profile,
        )
    }

    /**
     * Trims the description to the first sentence ending in a full stop,
     * capped at [DESC_MAX_CHARS]. Falls back to a hard char truncation with
     * an ellipsis if no sentence boundary is found within the limit.
     */
    private fun truncateDescription(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length <= DESC_MAX_CHARS) return trimmed

        // Try to end on a sentence boundary within the char limit.
        val window = trimmed.substring(0, DESC_MAX_CHARS)
        val dotIdx = window.lastIndexOf('.')
        return if (dotIdx > DESC_MAX_CHARS / 2) {
            trimmed.substring(0, dotIdx + 1)
        } else {
            window.trimEnd() + "…"
        }
    }

    /** Normalise team name to a cache key: lowercase, alphanumeric only. */
    private fun normaliseName(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]"), "")

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.requestMethod  = "GET"
            // TheSportsDB v1 free endpoint — no auth header needed.

            val code = conn.responseCode
            if (code != 200) {
                android.util.Log.e(TAG, "HTTP $code for $url")
                return null
            }
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "httpGet exception [$url]: $e")
            null
        }
    }
}