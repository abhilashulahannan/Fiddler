package com.example.fiddler.subapps.Fidland.phs3.navigation

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NavigationRepository
 *
 * Parses Google Maps navigation notifications posted by the system and
 * converts them into a [NavigationSnapshot] that the phs3 handler consumes.
 *
 * ── How to feed it ────────────────────────────────────────────────────────────
 * In your existing [NotificationListenerService], route Maps notifications here:
 *
 *   private val navRepo = NavigationRepository.instance
 *
 *   override fun onNotificationPosted(sbn: StatusBarNotification) {
 *       if (sbn.packageName == NavigationRepository.MAPS_PACKAGE) {
 *           navRepo.onNotification(sbn)
 *       }
 *   }
 *
 *   override fun onNotificationRemoved(sbn: StatusBarNotification) {
 *       if (sbn.packageName == NavigationRepository.MAPS_PACKAGE) {
 *           navRepo.onNavigationEnded()
 *       }
 *   }
 *
 * ── Notification structure ────────────────────────────────────────────────────
 * Google Maps posts a persistent navigation notification with:
 *   • contentTitle  → next manoeuvre label, e.g. "Turn left onto MG Road"
 *   • contentText   → distance to next turn, e.g. "In 350 m"
 *   • subText       → ETA or arrival time, e.g. "Arrive at 3:45 PM (14 min)"
 *
 * The big-text style sometimes surfaces additional upcoming steps via
 * EXTRA_BIG_TEXT or EXTRA_TEXT_LINES. We parse as many steps as available.
 *
 * ── Direction inference ───────────────────────────────────────────────────────
 * [inferDirection] maps common English phrases in the instruction text to a
 * [TurnDirection] enum. Add more patterns as needed for your locale.
 */
object NavigationRepository {

    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private val _flow = MutableStateFlow(EmptySnapshot)
    val flow: StateFlow<NavigationSnapshot> = _flow

    // ── Public API ─────────────────────────────────────────────────────────────

    fun onNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return

        val title    = extras.getString(Notification.EXTRA_TITLE)     ?: return
        val text     = extras.getString(Notification.EXTRA_TEXT)      ?: ""
        val subText  = extras.getString(Notification.EXTRA_SUB_TEXT)  ?: ""
        val bigText  = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val firstStep = NavStep(
            instruction    = title.trim(),
            distanceText   = cleanDistanceText(text),
            distanceMeters = parseDistanceMeters(text),
            direction      = inferDirection(title),
            trafficSeverity = TrafficSeverity.CLEAR,
        )

        // Try to parse additional steps from the big-text body.
        // Maps sometimes lists them as separate lines in the expanded notification.
        val extraSteps = parseExtraSteps(bigText, skip = title)

        val allSteps = listOf(firstStep) + extraSteps

        _flow.value = NavigationSnapshot(
            etaText      = parseEta(subText),
            arrivalTime  = parseArrivalTime(subText),
            steps        = allSteps,
            isActive     = true,
        )
    }

    fun onNavigationEnded() {
        _flow.value = EmptySnapshot
    }

    // ── Parsing helpers ────────────────────────────────────────────────────────

    /**
     * Strips the leading "In " prefix that Maps prepends to distances.
     * "In 350 m" → "350 m",  "In 1.2 km" → "1.2 km"
     */
    private fun cleanDistanceText(raw: String): String =
        raw.removePrefix("In ").removePrefix("in ").trim()

    /**
     * Converts a distance string to metres (approximate).
     * "350 m" → 350,  "1.2 km" → 1200,  "500 ft" → 152
     */
    private fun parseDistanceMeters(raw: String): Int {
        val clean = cleanDistanceText(raw)
        return try {
            when {
                clean.endsWith("km", ignoreCase = true) ->
                    (clean.removeSuffix("km").trim().toFloat() * 1000).toInt()
                clean.endsWith("mi", ignoreCase = true) ->
                    (clean.removeSuffix("mi").trim().toFloat() * 1609).toInt()
                clean.endsWith("ft", ignoreCase = true) ->
                    (clean.removeSuffix("ft").trim().toFloat() * 0.3048f).toInt()
                clean.endsWith("m", ignoreCase = true) ->
                    clean.removeSuffix("m").trim().toFloat().toInt()
                else -> 0
            }
        } catch (_: NumberFormatException) {
            0
        }
    }

    /**
     * Extracts just the "X min" portion from a Maps subText like
     * "Arrive at 3:45 PM (14 min)" or "14 min".
     */
    private fun parseEta(subText: String): String {
        val minMatch = Regex("""(\d+)\s*min""").find(subText)
        return minMatch?.let { "${it.groupValues[1]} min" } ?: subText.trim()
    }

    /**
     * Extracts the arrival clock time from a string like "Arrive at 3:45 PM (14 min)".
     * Returns empty string if not found.
     */
    private fun parseArrivalTime(subText: String): String {
        val timeMatch = Regex("""(\d{1,2}:\d{2}\s*[AP]M)""", RegexOption.IGNORE_CASE).find(subText)
        return timeMatch?.groupValues?.get(1) ?: ""
    }

    /**
     * Parses additional upcoming steps from the expanded notification big-text body.
     * Each line that looks like a step (contains a distance marker) is treated as one step.
     */
    private fun parseExtraSteps(bigText: String, skip: String): List<NavStep> {
        if (bigText.isBlank()) return emptyList()
        return bigText.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                        !line.startsWith(skip.take(20), ignoreCase = true) &&
                        (line.contains(" m", ignoreCase = true) || line.contains(" km", ignoreCase = true))
            }
            .take(5) // cap to keep State5 list manageable
            .mapIndexed { i, line ->
                // Lines often look like: "Turn right onto NH-48 · 2.3 km"
                val parts = line.split("·", "•", "–", "-").map { it.trim() }
                val instruction = parts.getOrNull(0) ?: line
                val distRaw     = parts.getOrNull(1) ?: ""
                NavStep(
                    instruction    = instruction,
                    distanceText   = distRaw.ifBlank { "–" },
                    distanceMeters = parseDistanceMeters(distRaw),
                    direction      = inferDirection(instruction),
                )
            }
    }

    /**
     * Infers a [TurnDirection] from the instruction text.
     * India drives on the left, so "keep left" is a normal lane-keep.
     * Extend with regional synonyms as needed.
     */
    fun inferDirection(instruction: String): TurnDirection {
        val lower = instruction.lowercase(Locale.getDefault())
        return when {
            // U-turns
            "u-turn" in lower || "u turn" in lower || "uturn" in lower -> {
                if ("right" in lower) TurnDirection.U_TURN_RIGHT else TurnDirection.U_TURN_LEFT
            }
            // Sharp
            "sharp left"  in lower -> TurnDirection.SHARP_LEFT
            "sharp right" in lower -> TurnDirection.SHARP_RIGHT
            // Mild / slight
            "slight left"  in lower ||
                    "keep left"    in lower ||
                    "bear left"    in lower -> TurnDirection.MILD_LEFT
            "slight right" in lower ||
                    "keep right"   in lower ||
                    "bear right"   in lower -> TurnDirection.MILD_RIGHT
            // Plain turns
            "turn left"  in lower || "left"  in lower -> TurnDirection.LEFT
            "turn right" in lower || "right" in lower -> TurnDirection.RIGHT
            // Straight
            "continue"    in lower ||
                    "straight"    in lower ||
                    "head"        in lower -> TurnDirection.STRAIGHT
            else -> TurnDirection.UNKNOWN
        }
    }
}