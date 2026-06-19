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
 * AfRequestLog — persistent, self-pruning request log for api-football calls.
 *
 * ── What it does ──────────────────────────────────────────────────────────────
 *
 *   Every time the app is about to fire a v3/fixtures?live=all call, it first
 *   asks this log two questions:
 *
 *     1. Has a call been made within the last [minSpacingMs]?
 *        If yes → BLOCK the call (frequency guard).
 *
 *     2. How many calls have been made today (UTC)?
 *        If ≥ [dailyCap] → BLOCK the call (daily cap guard).
 *
 *   If both checks pass, the caller fires the request and then records it here
 *   via [record]. Records older than [RETENTION_MS] (7 days) are pruned on
 *   every write so the file never grows unbounded.
 *
 * ── Persistence ───────────────────────────────────────────────────────────────
 *
 *   Stored as a JSON array in the app's internal files directory:
 *     <filesDir>/football/af_request_log.json
 *
 *   Each entry:
 *     { "ts": 1718694897123, "window": true }
 *       ts     — epoch ms of the call
 *       window — true if the call was fired inside an active match window
 *
 *   The file is read once at construction and kept in memory as [entries].
 *   All writes go through [flush] which serialises [entries] back to disk.
 *   Reads/writes happen on whatever coroutine calls [canFire] / [record] —
 *   the caller (ApiFootballSource) already dispatches to Dispatchers.IO.
 *
 * ── Why a file and not SharedPreferences ──────────────────────────────────────
 *   SharedPreferences is fine for scalar values but awkward for a growing list.
 *   A plain JSON file is readable directly in Android Studio's Device Explorer
 *   and trivial to export for debugging, which is useful when diagnosing the
 *   exact kind of budget burn shown in the api-football dashboard screenshot.
 *
 * @param context      Application or service context (used for [filesDir]).
 * @param minSpacingMs Minimum milliseconds that must have elapsed since the
 *                     last recorded call before a new one is allowed.
 *                     Default: 90 seconds.
 * @param dailyCap     Maximum calls allowed per UTC calendar day.
 *                     Default: 100 (api-football free tier hard limit).
 */
class AfRequestLog(
    context: Context,
    val minSpacingMs: Long = MIN_SPACING_DEFAULT_MS,
    val dailyCap: Int      = DAILY_CAP_DEFAULT,
) {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AfRequestLog"

        /** Default minimum gap between calls: 90 seconds. */
        const val MIN_SPACING_DEFAULT_MS: Long = 90_000L

        /** Default daily cap matching api-football free tier. */
        const val DAILY_CAP_DEFAULT: Int = 100

        /** How long to keep log entries: 7 days. */
        private const val RETENTION_MS: Long = 7L * 24 * 60 * 60_000L

        private const val LOG_DIR  = "football"
        private const val LOG_FILE = "af_request_log.json"

        private const val KEY_TS     = "ts"
        private const val KEY_WINDOW = "window"

        private val UTC_DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private val UTC_DISPLAY_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    data class Entry(val timestampMs: Long, val inWindow: Boolean)

    /** In-memory log; loaded from disk at construction, written back on every [record]. */
    private val entries = mutableListOf<Entry>()

    private val logFile: File = File(context.filesDir, "$LOG_DIR/$LOG_FILE")

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        load()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Check whether a new api-football call is currently allowed.
     *
     * Returns a [CheckResult] describing the outcome. The caller should inspect
     * [CheckResult.allowed] before firing the HTTP request, and log
     * [CheckResult.reason] for diagnostics.
     *
     * This does NOT record anything — call [record] after a successful HTTP call.
     */
    fun canFire(nowMs: Long = System.currentTimeMillis()): CheckResult {
        val todayStr = UTC_DAY_FMT.format(Date(nowMs))

        // ── 1. Daily cap check ────────────────────────────────────────────────
        val todayCount = entries.count { UTC_DAY_FMT.format(Date(it.timestampMs)) == todayStr }
        if (todayCount >= dailyCap) {
            return CheckResult(
                allowed = false,
                reason  = "Daily cap reached: $todayCount/$dailyCap calls used today ($todayStr)",
                todayCount    = todayCount,
                lastCallMs    = entries.maxOfOrNull { it.timestampMs },
                msSinceLastCall = sinceLastCallMs(nowMs),
            )
        }

        // ── 2. Minimum spacing check ──────────────────────────────────────────
        val lastCallMs = entries.maxOfOrNull { it.timestampMs }
        if (lastCallMs != null) {
            val elapsed = nowMs - lastCallMs
            if (elapsed < minSpacingMs) {
                val waitMs = minSpacingMs - elapsed
                return CheckResult(
                    allowed = false,
                    reason  = "Too soon: last call was ${elapsed / 1000}s ago " +
                            "(min spacing ${minSpacingMs / 1000}s, wait ${waitMs / 1000}s more)",
                    todayCount      = todayCount,
                    lastCallMs      = lastCallMs,
                    msSinceLastCall = elapsed,
                )
            }
        }

        return CheckResult(
            allowed         = true,
            reason          = "OK — $todayCount/$dailyCap calls today, " +
                    "last call ${sinceLastCallMs(nowMs)?.let { "${it / 1000}s ago" } ?: "never"}",
            todayCount      = todayCount,
            lastCallMs      = lastCallMs,
            msSinceLastCall = sinceLastCallMs(nowMs),
        )
    }

    /**
     * Record a completed api-football call. Call this immediately after the
     * HTTP request is dispatched (before parsing the response, so a parsing
     * failure still counts against the budget).
     *
     * Automatically prunes entries older than [RETENTION_MS] and flushes to disk.
     */
    fun record(nowMs: Long = System.currentTimeMillis(), inWindow: Boolean = false) {
        entries += Entry(timestampMs = nowMs, inWindow = inWindow)
        pruneOld(nowMs)
        flush()

        val todayStr   = UTC_DAY_FMT.format(Date(nowMs))
        val todayCount = entries.count { UTC_DAY_FMT.format(Date(it.timestampMs)) == todayStr }
        Log.d(TAG, "Recorded AF call — $todayCount/$dailyCap today, inWindow=$inWindow")
    }

    /**
     * Human-readable summary of recent call frequency, useful for debugging.
     * Shows the last 10 entries, calls-per-hour for the last hour, and today's total.
     */
    fun frequencySummary(nowMs: Long = System.currentTimeMillis()): String {
        val todayStr   = UTC_DAY_FMT.format(Date(nowMs))
        val todayCount = entries.count { UTC_DAY_FMT.format(Date(it.timestampMs)) == todayStr }
        val lastHourCount = entries.count { nowMs - it.timestampMs <= 60 * 60_000L }
        val last10  = entries.sortedByDescending { it.timestampMs }.take(10)

        val sb = StringBuilder()
        sb.appendLine("=== AF Request Log Summary ===")
        sb.appendLine("Today ($todayStr): $todayCount / $dailyCap calls")
        sb.appendLine("Last 60 min: $lastHourCount calls")
        sb.appendLine("Total log entries: ${entries.size} (7-day window)")
        sb.appendLine("Last 10 calls:")
        last10.forEach { e ->
            val age = (nowMs - e.timestampMs) / 1000
            sb.appendLine("  ${UTC_DISPLAY_FMT.format(Date(e.timestampMs))} UTC  " +
                    "(${age}s ago)  window=${e.inWindow}")
        }
        return sb.toString().trimEnd()
    }

    /** How many calls have been made today (UTC). */
    fun todayCount(nowMs: Long = System.currentTimeMillis()): Int {
        val todayStr = UTC_DAY_FMT.format(Date(nowMs))
        return entries.count { UTC_DAY_FMT.format(Date(it.timestampMs)) == todayStr }
    }

    /** All entries in the log, sorted oldest-first. Exposed for debug UI / tests. */
    fun allEntries(): List<Entry> = entries.toList()

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun sinceLastCallMs(nowMs: Long): Long? {
        val last = entries.maxOfOrNull { it.timestampMs } ?: return null
        return nowMs - last
    }

    private fun pruneOld(nowMs: Long) {
        val cutoff = nowMs - RETENTION_MS
        val before = entries.size
        entries.removeAll { it.timestampMs < cutoff }
        val pruned = before - entries.size
        if (pruned > 0) Log.d(TAG, "Pruned $pruned entries older than 7 days")
    }

    private fun load() {
        try {
            if (!logFile.exists()) return
            val text = logFile.readText()
            val arr  = JSONArray(text)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                entries += Entry(
                    timestampMs = obj.getLong(KEY_TS),
                    inWindow    = obj.optBoolean(KEY_WINDOW, false),
                )
            }
            // Prune stale entries immediately on load.
            pruneOld(System.currentTimeMillis())
            Log.d(TAG, "Loaded ${entries.size} AF log entries from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load AF request log: $e — starting fresh")
            entries.clear()
        }
    }

    private fun flush() {
        try {
            logFile.parentFile?.mkdirs()
            val arr = JSONArray()
            entries.sortedBy { it.timestampMs }.forEach { e ->
                arr.put(JSONObject().apply {
                    put(KEY_TS, e.timestampMs)
                    put(KEY_WINDOW, e.inWindow)
                })
            }
            logFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush AF request log: $e")
        }
    }

    // ── CheckResult ───────────────────────────────────────────────────────────

    /**
     * Result of a [canFire] check.
     *
     * @param allowed         True if the call is safe to make right now.
     * @param reason          Human-readable explanation (log this).
     * @param todayCount      How many calls have been made today.
     * @param lastCallMs      Epoch ms of the most recent recorded call, or null if none.
     * @param msSinceLastCall Milliseconds since the last call, or null if no prior call.
     */
    data class CheckResult(
        val allowed: Boolean,
        val reason: String,
        val todayCount: Int,
        val lastCallMs: Long?,
        val msSinceLastCall: Long?,
    )
}