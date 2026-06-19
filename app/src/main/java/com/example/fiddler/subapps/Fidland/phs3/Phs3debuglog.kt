package com.example.fiddler.subapps.Fidland.phs3

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Phs3DebugLog
 *
 * Lightweight in-memory log for phs3 entity lifecycle events.
 * Captures every register / unregister call with a timestamp so you
 * can dump the full history and diagnose which entities are qualifying
 * (or not).
 *
 * ── Wire-up ──────────────────────────────────────────────────────────────────
 * Call from Phs3Manager.register / unregister:
 *
 *   fun register(handler: Phs3Handler) {
 *       Phs3DebugLog.onRegister(handler.label)
 *       ...
 *   }
 *
 *   fun unregister(label: String) {
 *       Phs3DebugLog.onUnregister(label)
 *       ...
 *   }
 *
 * Also call from each trigger's start():
 *
 *   fun start() {
 *       Phs3DebugLog.onTriggerStart("Football")
 *       ...
 *   }
 *
 * ── Reading the log ───────────────────────────────────────────────────────────
 * Call Phs3DebugLog.dump() anywhere — e.g. a button in FidlandScreen,
 * or on a timer. The dump prints to logcat under tag "Phs3DebugLog"
 * and returns the same string so you can display it in UI if needed.
 *
 * Logcat filter:
 *   adb logcat -s Phs3DebugLog
 */
object Phs3DebugLog {

    private const val TAG = "Phs3DebugLog"
    private const val MAX_ENTRIES = 200

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class Entry(
        val timeMs: Long,
        val type: Type,
        val label: String,
        val extra: String = "",
    ) {
        enum class Type { TRIGGER_START, TRIGGER_STOP, REGISTER, UNREGISTER, POLL }

        override fun toString(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timeMs))
            val tag = when (type) {
                Type.TRIGGER_START -> "START   "
                Type.TRIGGER_STOP  -> "STOP    "
                Type.REGISTER      -> "REGISTER"
                Type.UNREGISTER    -> "UNREG   "
                Type.POLL          -> "POLL    "
            }
            return "[$time] $tag  $label${if (extra.isNotBlank()) "  |  $extra" else ""}"
        }
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    // ── API — call from triggers ──────────────────────────────────────────────

    /** Call at the top of each trigger's start(). */
    fun onTriggerStart(label: String) = append(Entry.Type.TRIGGER_START, label)

    /** Call at the top of each trigger's stop(). */
    fun onTriggerStop(label: String) = append(Entry.Type.TRIGGER_STOP, label)

    /**
     * Call from each trigger's poll / collect loop to show it's alive
     * and what it found. Keep [extra] short — e.g. "matches=3 live=1".
     */
    fun onPoll(label: String, extra: String) = append(Entry.Type.POLL, label, extra)

    // ── API — call from Phs3Manager ───────────────────────────────────────────

    /** Call from Phs3Manager.register, after the duplicate-check passes. */
    fun onRegister(label: String, qualifiedNow: List<String>) =
        append(Entry.Type.REGISTER, label, "qualified=${qualifiedNow}")

    /** Call from Phs3Manager.unregister, before removal. */
    fun onUnregister(label: String, qualifiedNow: List<String>) =
        append(Entry.Type.UNREGISTER, label, "qualified=${qualifiedNow}")

    // ── Dump ─────────────────────────────────────────────────────────────────

    /**
     * Prints the full log to logcat and returns it as a string.
     * Each entry is its own Log.d line so logcat doesn't truncate long lines.
     */
    fun dump(): String {
        val snapshot = entries.toList()
        if (snapshot.isEmpty()) {
            Log.d(TAG, "── Phs3DebugLog: no entries ──")
            return "no entries"
        }

        Log.d(TAG, "── Phs3DebugLog dump (${snapshot.size} entries) ──────────────")
        snapshot.forEach { Log.d(TAG, it.toString()) }
        Log.d(TAG, "── end ──────────────────────────────────────────────────────")

        return snapshot.joinToString("\n")
    }

    /** Clears all entries. */
    fun clear() = entries.clear()

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun append(type: Entry.Type, label: String, extra: String = "") {
        val entry = Entry(System.currentTimeMillis(), type, label, extra)
        // Also log immediately so you see events in real time without calling dump()
        Log.d(TAG, entry.toString())
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(entry)
    }
}