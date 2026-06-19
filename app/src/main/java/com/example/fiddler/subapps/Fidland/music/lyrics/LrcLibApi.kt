package com.example.fiddler.subapps.Fidland.music.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A single time-synced lyric line, parsed from an LRC-format `syncedLyrics` blob.
 *
 * [timeMs] is the timestamp at which this line should become the "current" line.
 * [text] may be empty for instrumental gaps — still useful to keep so the UI can
 * show a blank/"♪" placeholder during long instrumental sections.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Result of a lookup against LRCLIB — either plain or time-synced lyrics
 * (or both), plus the metadata LRCLIB matched against.
 *
 * [isInstrumental] mirrors LRCLIB's `instrumental` flag — render a single
 * "Instrumental" placeholder line in the UI when true and both lyric fields
 * are empty.
 */
data class LrcLibResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationSec: Int,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val isInstrumental: Boolean
) {
    /** Parses [syncedLyrics] into a sorted list of [LyricLine]s, or empty if none. */
    fun parsedSyncedLines(): List<LyricLine> {
        val raw = syncedLyrics ?: return emptyList()
        return parseLrc(raw)
    }
}

/**
 * Thin client for the LRCLIB public API (https://lrclib.net/docs).
 *
 * No API key required. Two endpoints are used:
 *   - GET /api/get        — exact match by track/artist/album/duration (preferred:
 *                            duration tolerance of ±2s lets LRCLIB find the right
 *                            recording even if our reported duration is slightly off).
 *   - GET /api/search      — fuzzy fallback when /get returns 404 (e.g. YT Music
 *                            titles often contain extra text like "(Official Video)").
 *
 * All network calls are blocking — callers must invoke from a background
 * coroutine (e.g. Dispatchers.IO), never from the main/UI thread.
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "Fiddler-Phs3-Music/1.0 (+https://github.com/fiddler)"
    private const val TIMEOUT_MS = 8000

    /**
     * Exact lookup. [durationSec] should be the track's total length in seconds —
     * LRCLIB uses it (±2s) to disambiguate between multiple recordings of the
     * same title. Pass 0 if unknown; LRCLIB will still attempt a best-effort match.
     *
     * Returns null on 404 (no match) or any network/parse error.
     */
    fun get(
        trackName: String,
        artistName: String,
        albumName: String = "",
        durationSec: Int = 0
    ): LrcLibResult? {
        return try {
            val url = buildString {
                append("$BASE_URL/get")
                append("?track_name=").append(enc(trackName))
                append("&artist_name=").append(enc(artistName))
                if (albumName.isNotBlank()) append("&album_name=").append(enc(albumName))
                if (durationSec > 0) append("&duration=").append(durationSec)
            }
            val (code, body) = httpGet(url)
            if (code != 200 || body == null) return null
            parseResult(JSONObject(body))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fuzzy search fallback — returns the best-ranked candidate, or null.
     * Used when [get] 404s, typically because the player-reported title/artist
     * don't exactly match LRCLIB's catalog (common with YT Music's verbose titles).
     */
    fun search(
        trackName: String,
        artistName: String,
        albumName: String = ""
    ): LrcLibResult? {
        return try {
            val url = buildString {
                append("$BASE_URL/search")
                append("?track_name=").append(enc(trackName))
                if (artistName.isNotBlank()) append("&artist_name=").append(enc(artistName))
                if (albumName.isNotBlank()) append("&album_name=").append(enc(albumName))
            }
            val (code, body) = httpGet(url)
            if (code != 200 || body == null) return null
            val arr = JSONArray(body)
            if (arr.length() == 0) return null

            // Prefer the first result that has synced lyrics; otherwise take the first.
            var best: JSONObject? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (best == null) best = obj
                if (!obj.isNull("syncedLyrics") && obj.optString("syncedLyrics").isNotBlank()) {
                    best = obj
                    break
                }
            }
            best?.let { parseResult(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Convenience: try [get] first, fall back to [search] on miss.
     * This is the entry point [LyricsRepository] should call for a network fetch.
     */
    fun fetch(
        trackName: String,
        artistName: String,
        albumName: String = "",
        durationSec: Int = 0
    ): LrcLibResult? {
        get(trackName, artistName, albumName, durationSec)?.let { return it }
        return search(trackName, artistName, albumName)
    }

    private fun parseResult(obj: JSONObject): LrcLibResult = LrcLibResult(
        id = obj.optLong("id", -1L),
        trackName = obj.optString("trackName"),
        artistName = obj.optString("artistName"),
        albumName = obj.optString("albumName"),
        durationSec = obj.optInt("duration", 0),
        plainLyrics = obj.optString("plainLyrics").takeIf { it.isNotBlank() },
        syncedLyrics = obj.optString("syncedLyrics").takeIf { it.isNotBlank() },
        isInstrumental = obj.optBoolean("instrumental", false)
    )

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Returns (responseCode, body) — body is null on non-2xx or I/O error. */
    private fun httpGet(urlStr: String): Pair<Int, String?> {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
            }
            code to body
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Parses an LRC-format string (e.g. "[01:02.34]Some lyric line") into sorted
 * [LyricLine]s. Lines without a leading timestamp are skipped. Metadata tags
 * like [ar:...], [ti:...], [al:...], [length:...] are ignored.
 */
fun parseLrc(lrc: String): List<LyricLine> {
    val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
    val lines = mutableListOf<LyricLine>()

    for (rawLine in lrc.lines()) {
        val matches = timeTag.findAll(rawLine).toList()
        if (matches.isEmpty()) continue

        // Text is everything after the LAST timestamp tag on the line
        // (a line can carry multiple timestamps for the same lyric).
        val lastMatch = matches.last()
        val text = rawLine.substring(lastMatch.range.last + 1).trim()

        for (m in matches) {
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val fraction = m.groupValues[3]
            val millis = when {
                fraction.isEmpty() -> 0L
                fraction.length == 1 -> fraction.toLong() * 100
                fraction.length == 2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            val timeMs = (minutes * 60_000L) + (seconds * 1000L) + millis
            lines.add(LyricLine(timeMs, text))
        }
    }

    return lines.sortedBy { it.timeMs }
}
