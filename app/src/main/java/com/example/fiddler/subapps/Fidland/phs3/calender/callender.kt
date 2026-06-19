package com.example.fiddler.subapps.Fidland.phs3.calender

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import kotlinx.coroutines.delay

/**
 * Phs3 module — Calendar / upcoming event.
 *
 * Indicator (State 3, location "b" — single text-column slot, no icon zone):
 *   Two-line layout matching MusicPhs3Handler's text column:
 *     line 1 (bold)  — event title
 *     line 2 (dim)   — time until start, "Now", "All day", or clock time
 *                       once more than an hour out (see [indicatorSubtitle]).
 *   Re-evaluates [nextIndicatorEvent] every tick so the indicator advances
 *   to the next event once the current one ends, and refreshes the
 *   relative-time subtitle every 30s.
 *
 * ControlsPanel (State 5):
 *   Scrollable, sectioned list — Today / Tomorrow / Upcoming this week
 *   (see [groupEventsForState5]). Each row shows a colour dot, title, time
 *   range (or "All day"), and location if present. Empty sections are
 *   omitted; an empty overall list shows a "No upcoming events" placeholder.
 *
 * @param events List of upcoming events (today through next 7 days). The
 *                hosting trigger should refresh this periodically from
 *                CalendarProvider and reconstruct the handler, or wrap it in
 *                a mutableStateOf if live in-place updates are preferred.
 */
class CalendarPhs3Handler(
    private val events: List<CalendarEvent>
) : Phs3Handler {

    override val label: String = "Calendar"

    @Composable
    override fun Indicator() {
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(30_000L) // refresh relative time every 30s
            }
        }

        val event = nextIndicatorEvent(events, nowMs)

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(CALENDAR_TEXT_COLUMN_WIDTH),
        ) {
            Text(
                text = event?.title?.ifBlank { "Untitled event" } ?: "No events today",
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = event?.indicatorSubtitle(nowMs) ?: "",
                color = Color(0xFFAAAAAA),
                fontSize = 7.sp,
                lineHeight = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    override fun State5Content() {
        val groups = remember(events) { groupEventsForState5(events) }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No upcoming events",
                    color = Color(0xFF888888),
                    fontSize = 13.sp
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValuesAll(top = 8.dp, bottom = 12.dp)
        ) {
            groups.forEach { group ->
                item {
                    Text(
                        text = group.section.title,
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                items(group.events) { event ->
                    CalendarEventRow(event)
                }
            }
        }
    }
}

// ── Layout sizing ────────────────────────────────────────────────────────
// Mirrors the music module's text-column width (MUSIC_TEXT_COLUMN_WIDTH).
// Calendar titles tend to run longer, so this is a bit wider — adjust to
// taste or move into IslandConfig if you want it centrally tunable.
private val CALENDAR_TEXT_COLUMN_WIDTH: Dp = 70.dp

/** A single State 5 event row: colour dot, title, time range, location. */
@Composable
private fun CalendarEventRow(event: CalendarEvent) {
    val dotColor = event.colorHex?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    } ?: Color(0xFF7C8CFF)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: open event details */ }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title.ifBlank { "Untitled event" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = if (event.location.isNullOrBlank()) {
                event.timeRangeLabel()
            } else {
                "${event.timeRangeLabel()} · ${event.location}"
            }
            Text(
                text = sub,
                color = Color(0xFF888888),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** Small helper so contentPadding reads cleanly without importing PaddingValues directly at call sites. */
private fun PaddingValuesAll(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
) = androidx.compose.foundation.layout.PaddingValues(top = top, bottom = bottom)