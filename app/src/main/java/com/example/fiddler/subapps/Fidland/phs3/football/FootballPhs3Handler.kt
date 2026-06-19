package com.example.fiddler.subapps.Fidland.phs3.football

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import kotlinx.coroutines.delay

/**
 * Phs3 module — Live Football.
 *
 * Qualifies whenever at least one match from the five tracked competitions
 * is live or kicking off within 30 minutes. Constructed by
 * [FootballPhs3Trigger], which owns the [FootballRepository] and calls
 * service.activatePhs3(FootballPhs3Handler(repo)) when appropriate.
 *
 * ── Location a (left of hole-punch) ──────────────────────────────────────────
 *   Flashes a [FootballEventIcon] for [FLASH_DURATION_MS] (30 s) when a goal,
 *   yellow card, or red card is detected in the most-recently-focused match.
 *   Then goes blank. No icon = no recent event.
 *
 * ── Location b (immediate right of hole-punch) ────────────────────────────────
 *   [HomeLogoIcon]  homeScore : awayScore  [AwayLogoIcon]
 *   Shows the focused match (first live match, or first in list if none live).
 *
 * ── State 5 (ControlsPanel — long-press to open) ─────────────────────────────
 *   HorizontalPager — one page per live/today match.
 *   Each page shows:
 *     • Competition name + match status
 *     • Matchday and venue (when known — football-data.org only)
 *     • Team logos + full team names + score
 *     • Full goal / card / substitution event timeline, scrollable,
 *       most-recent first
 *   Page-indicator dots at the bottom reflect the number of live matches.
 *   Swipe left/right to switch between matches.
 *
 * @param repo  The shared [FootballRepository] providing match and event flows.
 */
class FootballPhs3Handler(
    private val repo: FootballRepository,
) : Phs3Handler {

    override val label: String = "Football"

    // ─────────────────────────────────────────────────────────────────────────
    //  Location a — event flash icon
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shown in the LEFT ZONE (location a).
     *
     * Collects [FootballRepository.flashEventFlow] and displays the icon for
     * exactly [FLASH_DURATION_MS] milliseconds, then hides it.
     */
    @Composable
    fun LocationAIndicator() {
        val flashEvent by repo.flashEventFlow.collectAsState()
        var visibleEvent by remember { mutableStateOf<FlashEvent?>(null) }

        // When a new flash event arrives, display it and auto-clear after 30 s.
        LaunchedEffect(flashEvent) {
            val ev = flashEvent ?: return@LaunchedEffect
            visibleEvent = ev
            delay(FLASH_DURATION_MS)
            // Only clear if still showing this exact event (no newer one arrived).
            if (visibleEvent == ev) visibleEvent = null
        }

        AnimatedVisibility(
            visible = visibleEvent != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            visibleEvent?.let { ev ->
                FootballEventIcon(type = ev.type, size = 18.dp)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Indicator — location b (right of hole-punch)
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    override fun Indicator() {
        val matches by repo.matchesFlow.collectAsState()
        // Focus: first live match, or first scheduled match today.
        val focused = matches.firstOrNull { it.isActive() } ?: matches.firstOrNull()

        if (focused == null) {
            // No matches today — show a dim ball so the slot isn't empty.
            FootballEventIcon(type = EventType.GOAL, size = 16.dp)
            return
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Home crest
            TeamCrest(url = focused.homeLogoUrl, teamName = focused.homeTeam, size = 16.dp)

            // Score (or kick-off time if not started)
            Text(
                text = if (focused.status == MatchStatus.SCHEDULED)
                    focused.kickoffTime()
                else
                    focused.scoreLabel(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            // Away crest
            TeamCrest(url = focused.awayLogoUrl, teamName = focused.awayTeam, size = 16.dp)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  State 5 — swipeable match detail panel
    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        val matches by repo.matchesFlow.collectAsState()
        // State 5 shows all live matches first, then today's scheduled ones.
        val displayMatches = matches.ifEmpty { return }

        val pagerState = rememberPagerState(pageCount = { displayMatches.size })

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(top = 15.dp)) {

            // ── Match pager ─────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                MatchDetailPage(match = displayMatches[page])
            }

            // ── Page dots ───────────────────────────────────────────────────
            if (displayMatches.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                ) {
                    displayMatches.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == pagerState.currentPage) 4.dp else 2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage)
                                        Color.White
                                    else
                                        Color(0xFF444444)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Match detail page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchDetailPage(match: FootballMatch) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {

        // ── Competition + status ─────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = match.competition.displayName,
                color = Color(0xFF888888),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(match)

            // ── Matchday + venue ─────────────────────────────────────────────────
            // Only football-data.org provides these — null on OLDB/AF-only matches,
            // in which case this row collapses to nothing rather than showing blanks.
            val matchdayText = match.matchdayLabel()
            val venueText    = match.venue
            if (matchdayText != null || venueText != null) {
                Text(
                    text = listOfNotNull(matchdayText, venueText).joinToString("  ·  "),
                    color = Color(0xFF666666),
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }



        Spacer(modifier = Modifier.height(6.dp))

        // ── Score row ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Home
            TeamColumn(
                logoUrl = match.homeLogoUrl,
                name = match.homeTeam,
                modifier = Modifier.weight(1f),
                align = Alignment.Start,
            )

            // Score / colon
            Text(
                text = match.scoreLabel(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // Away
            TeamColumn(
                logoUrl = match.awayLogoUrl,
                name = match.awayTeam,
                modifier = Modifier.weight(1f),
                align = Alignment.End,
            )
        }

        // ── Event timeline ───────────────────────────────────────────────────
        // Full timeline (not just the last 4) — most recent first, scrollable
        // so matches with many events don't overflow the fixed-height pill panel.
        if (match.events.isNotEmpty()) {
            S5Divider()
            val orderedEvents = match.events.reversed()
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(orderedEvents) { ev ->
                    EventLogRow(event = ev)
                }
            }
        }
    }
}

@Composable
private fun TeamColumn(
    logoUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal,
) {
    Column(
        horizontalAlignment = align,
        modifier = modifier,
    ) {
        TeamCrest(url = logoUrl, teamName = name, size = 26.dp)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = name,
            color = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBadge(match: FootballMatch) {
    val (bgColor, textColor) = when (match.status) {
        MatchStatus.LIVE -> Color(0xFF1A3A1A) to Color(0xFF4ADE80)
        MatchStatus.HALF_TIME -> Color(0xFF1A2A3A) to Color(0xFF60A5FA)
        MatchStatus.FINISHED -> Color(0xFF1E1E1E) to Color(0xFF888888)
        else -> Color(0xFF1E1E1E) to Color(0xFF888888)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = match.statusLabel(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EventLogRow(event: MatchEvent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        // Mini event icon
        FootballEventIcon(type = event.type, size = 12.dp)
        Spacer(modifier = Modifier.width(6.dp))
        // Minute
        Text(
            text = event.minute?.let { "${it}'" } ?: "",
            color = Color(0xFF666666),
            fontSize = 9.sp,
            modifier = Modifier.width(20.dp),
        )
        // Player name + team
        Text(
            text = buildString {
                event.playerName?.let { append(it) }
                if (event.playerName != null) append("  ")
                append(event.teamName)
            },
            color = Color(0xFFCCCCCC),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Team crest loaded from [url] via Coil's [AsyncImage].
 * Falls back to a dim circle with the team's first letter if the image fails.
 *
 * Requires Coil in your Gradle dependencies:
 *   implementation("io.coil-kt:coil-compose:2.6.0")
 */
@Composable
private fun TeamCrest(url: String?, teamName: String, size: Dp) {
    val context = LocalContext.current
    if (url != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = teamName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    } else {
        // Fallback: dim circle with initial letter.
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = teamName.firstOrNull()?.uppercase() ?: "?",
                color = Color(0xFF666666),
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

/** Thin horizontal rule — matches the style used in AlarmPhs3Handler. */
@Composable
private fun S5Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}