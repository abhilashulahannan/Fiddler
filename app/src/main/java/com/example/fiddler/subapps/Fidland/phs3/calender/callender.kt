package com.example.fiddler.subapps.Fidland.phs3.calender

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
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
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon
import kotlinx.coroutines.delay

/**
 * Phs3 module — Calendar / upcoming event.
 *
 * Indicator (State 3, location "b" — icon slot + text-column, matching
 *   MusicPhs3Handler's icon + text-column layout):
 *   Looping `callender.json` glyph (see [CalendarIcon]) in a fixed-width
 *   slot, followed by the same two-line text column as before:
 *     line 1 (bold)  — event title
 *     line 2 (dim)   — time until start, "Now", "All day", or clock time
 *                       once more than an hour out (see [indicatorSubtitle]).
 *   Re-evaluates [nextIndicatorEvent] every tick so the indicator advances
 *   to the next event once the current one ends, and refreshes the
 *   relative-time subtitle every 30s.
 *
 * State 5 (redesigned):
 *   Two-tier layout instead of a flat list:
 *     1. A pinned "Next up" hero card ([NextUpHero]) for the soonest
 *        ongoing/upcoming event — bigger title, colour-tinted countdown
 *        pill, location row with icon.
 *     2. A scrollable, sectioned list of everything else — Today /
 *        Tomorrow / Upcoming this week (see [groupEventsForState5]),
 *        rendered as rounded cards with a coloured accent bar instead of
 *        the old plain dot-row, styled section headers (small caps label
 *        + accent rule), and an icon-based empty state.
 *   The hero event is excluded from its section in the list below so it
 *   isn't shown twice.
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CALENDAR_ICON_TEXT_GAP),
            modifier = Modifier.width(CALENDAR_ICON_WIDTH + CALENDAR_ICON_TEXT_GAP + CALENDAR_TEXT_COLUMN_WIDTH),
        ) {
            Box(
                modifier = Modifier.width(CALENDAR_ICON_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                CalendarIcon(size = CALENDAR_ICON_SIZE)
            }

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
    }

    @Composable
    override fun State5Content() {
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(30_000L)
            }
        }

        val hero = remember(events, nowMs) { nextIndicatorEvent(events, nowMs) }
        val groups = remember(events, hero) {
            groupEventsForState5(events)
                .map { group -> group.copy(events = group.events.filter { it !== hero }) }
                .filter { it.events.isNotEmpty() }
        }

        if (hero == null && groups.isEmpty()) {
            EmptyState()
            return
        }

        Column(modifier = Modifier.fillMaxSize()) {
            hero?.let {
                NextUpHero(event = it, nowMs = nowMs)
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (groups.isEmpty()) return@Column

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValuesAll(bottom = 12.dp)
            ) {
                groups.forEach { group ->
                    item {
                        SectionHeader(title = group.section.title)
                    }
                    val showDate = group.section == CalendarSection.THIS_WEEK
                    items(group.events) { event ->
                        EventCard(event, showDate = showDate)
                    }
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

// Icon slot — same idiom as MUSIC_EQ_WIDTH/MUSIC_EQ_TEXT_GAP in IslandConfig,
// kept local here since Calendar's icon (unlike the equalizer) is a fixed
// static glyph with no per-frame layout dependencies elsewhere.
private val CALENDAR_ICON_SIZE: Dp = 16.dp
private val CALENDAR_ICON_WIDTH: Dp = 18.dp
private val CALENDAR_ICON_TEXT_GAP: Dp = 6.dp

/** Looping `callender.json` glyph shown in the Indicator's new icon slot. */
@Composable
private fun CalendarIcon(size: Dp) {
    MonoLottieIcon(rawRes = R.raw.callender, size = size)
}

/** Resolves an event's accent colour, falling back to a neutral indigo. */
private fun CalendarEvent.accentColor(): Color =
    colorHex?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
        ?: Color(0xFF7C8CFF)

// ── State 5: Next-up hero card ──────────────────────────────────────────
// Pinned above the list — the one thing worth glancing at without
// scrolling. Bigger type, a colour-tinted countdown pill instead of plain
// grey subtitle text, and a location row when present.
@Composable
private fun NextUpHero(event: CalendarEvent, nowMs: Long) {
    val accent = event.accentColor()

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable { /* TODO: open event details */ }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "NEXT UP",
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = event.title.ifBlank { "Untitled event" },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (event.startsToday(nowMs)) event.timeRangeLabel() else event.dateTimeRangeLabel(),
                color = Color(0xFFCCCCCC),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!event.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = event.location,
                        color = Color(0xFF999999),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.22f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = event.indicatorSubtitle(nowMs),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

// ── State 5: section header ─────────────────────────────────────────────
// Small-caps label with a short accent rule instead of the old plain grey
// text — same idiom used for the "NEXT UP" hero eyebrow, so the list
// reads as a continuation of the hero card rather than a separate block.
@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF555555))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title.uppercase(),
            color = Color(0xFF777777),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

// ── State 5: event card ─────────────────────────────────────────────────
// Replaces the old flat dot-row: rounded card, subtle background tint, and
// a coloured accent bar down the left edge instead of a small dot — reads
// as a card list rather than a plain text list.
@Composable
private fun EventCard(event: CalendarEvent, showDate: Boolean = false) {
    val accent = event.accentColor()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { /* TODO: open event details */ }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title.ifBlank { "Untitled event" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (showDate) event.dateTimeRangeLabel() else event.timeRangeLabel(),
                    color = Color(0xFF999999),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = "  ·  ",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = event.location,
                        color = Color(0xFF999999),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── State 5: empty state ────────────────────────────────────────────────
// Icon + text instead of bare centred text, matching the visual weight of
// the rest of the redesigned panel.
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFF444444),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No upcoming events",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
        }
    }
}

/** Small helper so contentPadding reads cleanly without importing PaddingValues directly at call sites. */
private fun PaddingValuesAll(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
) = androidx.compose.foundation.layout.PaddingValues(top = top, bottom = bottom)