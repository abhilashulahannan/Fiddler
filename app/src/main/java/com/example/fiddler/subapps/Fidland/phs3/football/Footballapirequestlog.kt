package com.example.fiddler.subapps.Fidland.phs3.football

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * FootballApiRequestLog — persistent, self-pruning request log shared by ALL
 * football data sources (football-data.org, OpenLigaDB, api-football, and
 * TheSportsDB).
 *
 * ── Why this exists ───────────────────────────────────────────────────────────
 *   [AfRequestLog] already tracks api-football calls precisely, because that
 *   source has a hard 100/day budget that must be enforced. The other three
 *   sources don't need enforcement, but the Football settings screen still
 *   wants to show the user "how many requests went out today" across every
 *   source — partly out of transparency, partly because football-data.org and
 *   OpenLigaDB are also rate-limited on their free tiers and it's useful to see
 *   the pattern. This log is a lightweight, read-mostly counter for that
 *   purpose; it does not gate or block any call.
 *
 * ── What it does ──────────────────────────────────────────────────────────────
 *   Each data source calls [record] immediately after dispatching an HTTP
 *   request (success or failure — a failed call still used the network).
 *   Entries are tagged with a [source] id so the UI can show a per-API
 *   breakdown as well as a combined total.
 *
 * ── Persistence ───────────────────────────────────────────────────────────────
 *   Stored as a JSON array in the app's internal files directory:
 *     <filesDir>/football/api_request_log.json
 *
 *   Each entry: { "ts": 1718694897123, "src": "football-data.org" }
 *
 *   The file is read fresh on every [FootballApiRequestLog] construction —
 *   instances are intentionally cheap and short-lived (one per [record] /
 *   [todayCounts] call from the UI) since multiple processes (the foreground
 *   service and the settings screen) may both touch this file. Every call
 *   re-reads from disk so the settings screen always reflects what the
 *   service most recently wrote, even though they don't share memory.
 *
 * @param context Application / service / activity context (used for [filesDir]).
 */
class FootballApiRequestLog(context: Context) {

    companion object {
        private const val TAG = "FootballApiRequestLog"

        /** How long to keep log entries: 7 days (mirrors [AfRequestLog]). */
        private const val RETENTION_MS: Long = 7L * 24 * 60 * 60_000L

        private const val LOG_DIR  = "football"
        private const val LOG_FILE = "api_request_log.json"

        private const val KEY_TS  = "ts"
        private const val KEY_SRC = "src"

        private val UTC_DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Canonical source ids. Every API that fires a request against any of
         * the four football data providers should record under one of these.
         */
        const val SOURCE_FOOTBALL_DATA_ORG = "football-data.org"
        const val SOURCE_OPENLIGADB        = "openligadb"
        const val SOURCE_API_FOOTBALL      = "api-football"
        const val SOURCE_THESPORTSDB       = "thesportsdb"

        /** Display order + labels for the settings screen. */
        val ALL_SOURCES_IN_DISPLAY_ORDER = listOf(
            SOURCE_FOOTBALL_DATA_ORG,
            SOURCE_OPENLIGADB,
            SOURCE_API_FOOTBALL,
            SOURCE_THESPORTSDB,
        )
    }

    data class Entry(val timestampMs: Long, val source: String)

    private val logFile: File = File(context.filesDir, "$LOG_DIR/$LOG_FILE")

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Record one outbound request to [source]. Safe to call from any thread;
     * does its own file I/O, so callers already on Dispatchers.IO (as every
     * source's httpGet caller is) pay no extra cost.
     */
    @Synchronized
    fun record(source: String, nowMs: Long = System.currentTimeMillis()) {
        val entries = load().toMutableList()
        entries += Entry(timestampMs = nowMs, source = source)
        val cutoff = nowMs - RETENTION_MS
        entries.removeAll { it.timestampMs < cutoff }
        flush(entries)
        Log.d(TAG, "Recorded call to '$source' — ${todayCountFor(entries, source, nowMs)} from that source today")
    }

    /**
     * Per-source request counts for the current UTC day, plus the combined
     * total. Read fresh from disk every call — cheap (the file is pruned to a
     * 7-day window) and always up to date with whatever the foreground
     * service most recently wrote.
     */
    fun todayCounts(nowMs: Long = System.currentTimeMillis()): TodaySnapshot {
        val entries   = load()
        val todayStr  = UTC_DAY_FMT.format(Date(nowMs))
        val todays    = entries.filter { UTC_DAY_FMT.format(Date(it.timestampMs)) == todayStr }

        val perSource = ALL_SOURCES_IN_DISPLAY_ORDER.associateWith { src ->
            todays.count { it.source == src }
        }

        return TodaySnapshot(
            dayUtcDate = todayStr,
            perSource  = perSource,
            total      = todays.size,
        )
    }

    data class TodaySnapshot(
        val dayUtcDate: String,
        val perSource: Map<String, Int>,
        val total: Int,
    )

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun todayCountFor(entries: List<Entry>, source: String, nowMs: Long): Int {
        val todayStr = UTC_DAY_FMT.format(Date(nowMs))
        return entries.count { it.source == source && UTC_DAY_FMT.format(Date(it.timestampMs)) == todayStr }
    }

    private fun load(): List<Entry> {
        return try {
            if (!logFile.exists()) return emptyList()
            val arr = JSONArray(logFile.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Entry(
                    timestampMs = obj.getLong(KEY_TS),
                    source      = obj.optString(KEY_SRC, "unknown"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load API request log: $e — treating as empty")
            emptyList()
        }
    }

    private fun flush(entries: List<Entry>) {
        try {
            logFile.parentFile?.mkdirs()
            val arr = JSONArray()
            entries.sortedBy { it.timestampMs }.forEach { e ->
                arr.put(JSONObject().apply {
                    put(KEY_TS, e.timestampMs)
                    put(KEY_SRC, e.source)
                })
            }
            logFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush API request log: $e")
        }
    }
}