package com.example.fiddler.subapps.Fidland.phs3.football

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

// ─────────────────────────────────────────────────────────────────────────────
//  Supplemental data models
//  These extend FootballMatch with the zero-cost fields from the API doc that
//  are already in the response payloads but not yet stored on the match model.
//  Wire these up in parseFdMatches / parseOldbMatches / TheSportsDbSource.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Club profile assembled from TheSportsDB's searchteams response.
 * All fields are already in the payload — just not extracted today.
 * Fetched during the existing badge-backfill pass; no extra API calls.
 *
 * @param teamName       Canonical team name (matches [FootballMatch.homeTeam] /
 *                       [FootballMatch.awayTeam] after normalisation).
 * @param founded        [intFormedYear] — founding year, e.g. 1902.
 * @param country        [strCountry] — registered country, e.g. "Spain".
 * @param stadium        [strStadium] — home ground name, e.g. "Santiago Bernabéu".
 * @param stadiumCapacity [intStadiumCapacity] — seat count, e.g. 81044.
 * @param stadiumThumbUrl [strStadiumThumb] + "/small" suffix — stadium photo URL.
 *                        Unique across all 4 APIs; only TSDB exposes this.
 * @param stadiumLocation [strStadiumLocation] — city the ground is in.
 * @param description    [strDescriptionEN] — short club bio (optional; shown in
 *                       Clubs tab if non-empty and under ~120 chars).
 */
data class ClubProfile(
    val teamName: String,
    val founded: Int? = null,
    val country: String? = null,
    val stadium: String? = null,
    val stadiumCapacity: Int? = null,
    val stadiumThumbUrl: String? = null,
    val stadiumLocation: String? = null,
    val description: String? = null,
)

/**
 * Extended match event — superset of [MatchEvent] adding OLDB-only fields.
 * Replace [MatchEvent] with this (or add fields directly to [MatchEvent]) when
 * wiring up the OLDB goal parser to extract isPenalty / isOwnGoal / assistName.
 *
 * For now the card falls back gracefully: [isPenalty] and [isOwnGoal] default
 * false, [assistName] defaults null, so existing [MatchEvent] data renders
 * identically to today and the new fields light up progressively as the parser
 * is extended.
 */
data class RichMatchEvent(
    val base: MatchEvent,
    val isPenalty: Boolean = false,
    val isOwnGoal: Boolean = false,
    val assistName: String? = null,
) {
    val id get() = base.id
    val type get() = base.type
    val minute get() = base.minute
    val playerName get() = base.playerName
    val teamName get() = base.teamName
}

/** Converts a plain [MatchEvent] to a [RichMatchEvent] with no extra data. */
fun MatchEvent.toRich() = RichMatchEvent(base = this)

/**
 * Everything the card needs beyond the core [FootballMatch] fields.
 * Pass a default instance if extended data hasn't been fetched yet — the card
 * degrades gracefully (empty Clubs / Venues tabs show a placeholder).
 *
 * @param homeClub       Club profile for the home team.
 * @param awayClub       Club profile for the away team.
 * @param richEvents     Events with pen / OG / assist annotations.
 *                       If null, the card falls back to [FootballMatch.events].
 * @param refereeLabel   Formatted as "Name · Nationality" — from FD referees[].
 * @param htScore        Half-time score string, e.g. "HT 1–0". From
 *                       FD score.halfTime (parsed today but not stored / shown).
 * @param scoreDuration  "REGULAR" | "EXTRA_TIME" | "PENALTY_SHOOTOUT" — from
 *                       FD score.duration. Drives the ET / Pens status badge.
 * @param homeTla        3-letter abbreviation, e.g. "RMA". From FD homeTeam.tla.
 * @param awayTla        3-letter abbreviation, e.g. "FCB". From FD awayTeam.tla.
 */
data class MatchDetailExtras(
    val homeClub: ClubProfile? = null,
    val awayClub: ClubProfile? = null,
    val richEvents: List<RichMatchEvent>? = null,
    val refereeLabel: String? = null,
    val htScore: String? = null,
    val scoreDuration: String? = null,
    val homeTla: String? = null,
    val awayTla: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Color palette (mirrors Footballtopic.kt + the HTML mockup)
// ─────────────────────────────────────────────────────────────────────────────

private object CardColors {
    val cardBg         = Color(0xFF161616)
    val cardBorder     = Color(0xFF2A2A2A)
    val cardBorderOpen = Color(0xFF333333)
    val rowDivider     = Color(0xFF1E1E1E)

    val textPrimary    = Color(0xFFFFFFFF)
    val textSecondary  = Color(0xFFCCCCCC)
    val textMuted      = Color(0xFF888888)
    val textDim        = Color(0xFF555555)

    val tabBg          = Color(0xFF111111)
    val tabActiveLine  = Color(0xFFFFFFFF)

    val liveBg         = Color(0xFF0D2416)
    val liveText       = Color(0xFF4ADE80)
    val htBg           = Color(0xFF0C1A2E)
    val htText         = Color(0xFF60A5FA)
    val etBg           = Color(0xFF1A1000)
    val etText         = Color(0xFFF59E0B)
    val ftBg           = Color(0xFF1E1E1E)
    val ftText         = Color(0xFF888888)
    val schedBg        = Color(0xFF1E1E1E)
    val schedText      = Color(0xFFAAAAAA)

    val goalBg         = Color(0xFF0D2416)
    val goalText       = Color(0xFF4ADE80)
    val yellowBg       = Color(0xFF1C1600)
    val yellowText     = Color(0xFFEAB308)
    val redBg          = Color(0xFF1A0000)
    val redText        = Color(0xFFEF4444)

    val crestBg        = Color(0xFF252525)
    val tagBg          = Color(0xFF1E1E1E)
    val sectionDivider = Color(0xFF222222)
    val venueBg        = Color(0xFF1A1A1A)
    val accentDot      = Color(0xFF6366F1)
}

// ─────────────────────────────────────────────────────────────────────────────
//  MatchDetailCard — public entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An expandable match card for the Football dashboard list.
 *
 * **Collapsed** — shows competition · matchday, status badge, crests, team
 * names, score (or "vs"), HT score sub-line, venue footer.
 *
 * **Expanded** — hero header (crests, score, TLA, country, live minute),
 * then three tabs:
 *   • Events  — goals with pen/OG/assist tags, yellow/red cards, referee line.
 *   • Clubs   — two-column grid: founded, country, home ground, capacity.
 *   • Venues  — match venue block + side-by-side club home grounds with thumb.
 *
 * @param match   Core match data from [FootballRepository].
 * @param extras  Extended data (clubs, rich events, referee, TLA…). Pass
 *                [MatchDetailExtras()] as a safe default — card degrades cleanly.
 */
@Composable
fun MatchDetailCard(
    match: FootballMatch,
    extras: MatchDetailExtras = MatchDetailExtras(),
) {
    var expanded by remember(match.id) { mutableStateOf(false) }
    var activeTab by remember(match.id) { mutableIntStateOf(0) }

    val borderColor = if (expanded) CardColors.cardBorderOpen else CardColors.cardBorder
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColors.cardBg)
            .border(
                width = 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { expanded = !expanded },
    ) {
        // ── Collapsed body ────────────────────────────────────────────────────
        CollapsedBody(match = match, extras = extras, chevronAngle = chevronAngle)

        // ── Expanded panel ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
        ) {
            Column {
                ExpandedHero(match = match, extras = extras)

                TabRow(activeTab = activeTab, onTabSelected = { activeTab = it })

                when (activeTab) {
                    0 -> EventsTab(match = match, extras = extras)
                    1 -> ClubsTab(extras = extras)
                    2 -> VenuesTab(match = match, extras = extras)
                }

                // Collapse chevron at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardColors.cardBg)
                        .border(
                            width = 0.5.dp,
                            color = CardColors.sectionDivider,
                            shape = RoundedCornerShape(0.dp),
                        )
                        .clickable { expanded = false }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = CardColors.textDim,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(180f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collapsed body
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollapsedBody(
    match: FootballMatch,
    extras: MatchDetailExtras,
    chevronAngle: Float,
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

        // Top row: matchday label + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val matchdayText = listOfNotNull(
                match.competition.displayName,
                match.matchdayLabel(),
            ).joinToString(" · ")
            Text(
                text = matchdayText,
                color = CardColors.textDim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(match = match, extras = extras)
        }

        Spacer(Modifier.height(9.dp))

        // Teams + score row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Home side
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamCrest(url = match.homeLogoUrl, name = match.homeTeam, size = 24)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = match.homeTeam,
                    color = CardColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Score / vs
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                if (match.homeScore != null && match.awayScore != null) {
                    Text(
                        text = "${match.homeScore}  :  ${match.awayScore}",
                        color = CardColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    if (extras.htScore != null) {
                        Text(
                            text = extras.htScore,
                            color = CardColors.textDim,
                            fontSize = 9.sp,
                        )
                    }
                } else {
                    Text(
                        text = "vs",
                        color = CardColors.textDim,
                        fontSize = 12.sp,
                    )
                }
            }

            // Away side
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = match.awayTeam,
                    color = CardColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.width(7.dp))
                TeamCrest(url = match.awayLogoUrl, name = match.awayTeam, size = 24)
            }
        }

        // Footer: venue + expand chevron
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val venueText = match.venue ?: ""
            if (venueText.isNotBlank()) {
                Text(
                    text = "📍 $venueText",
                    color = CardColors.textDim,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("details", color = CardColors.textDim, fontSize = 9.sp)
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CardColors.textDim,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(chevronAngle),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Expanded hero header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedHero(match: FootballMatch, extras: MatchDetailExtras) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF131313))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column {
            // Comp + date subline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = listOfNotNull(
                        match.competition.displayName,
                        match.matchdayLabel(),
                    ).joinToString(" · "),
                    color = CardColors.textDim,
                    fontSize = 10.sp,
                )
                StatusBadge(match = match, extras = extras)
            }

            Spacer(Modifier.height(14.dp))

            // Teams + score
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // Home team column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.homeLogoUrl, name = match.homeTeam, size = 44)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = match.homeTeam,
                        color = CardColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (extras.homeTla != null) {
                        Text(
                            text = extras.homeTla,
                            color = CardColors.textDim,
                            fontSize = 9.sp,
                            letterSpacing = 0.04.sp,
                        )
                    }
                    if (extras.homeClub?.country != null) {
                        Text(
                            text = extras.homeClub.country,
                            color = CardColors.textDim,
                            fontSize = 9.sp,
                        )
                    }
                }

                // Score column
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (match.homeScore != null && match.awayScore != null) {
                        Text(
                            text = "${match.homeScore} – ${match.awayScore}",
                            color = CardColors.textPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            lineHeight = 32.sp,
                        )
                        if (extras.htScore != null) {
                            Text(
                                text = extras.htScore,
                                color = CardColors.textDim,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    } else {
                        Text(
                            text = "–  :  –",
                            color = CardColors.textDim,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val ko = match.kickoffTime()
                        Text(
                            text = "KO $ko",
                            color = CardColors.textDim,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (match.status == MatchStatus.LIVE && match.minute != null) {
                        Text(
                            text = "● ${match.minute}'",
                            color = CardColors.liveText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                // Away team column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamCrest(url = match.awayLogoUrl, name = match.awayTeam, size = 44)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = match.awayTeam,
                        color = CardColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (extras.awayTla != null) {
                        Text(
                            text = extras.awayTla,
                            color = CardColors.textDim,
                            fontSize = 9.sp,
                            letterSpacing = 0.04.sp,
                        )
                    }
                    if (extras.awayClub?.country != null) {
                        Text(
                            text = extras.awayClub.country,
                            color = CardColors.textDim,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab row
// ─────────────────────────────────────────────────────────────────────────────

private val TAB_LABELS = listOf("Events", "Clubs", "Venues")

@Composable
private fun TabRow(activeTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColors.tabBg)
            .border(
                width = 0.5.dp,
                color = CardColors.sectionDivider,
                shape = RoundedCornerShape(0.dp),
            ),
    ) {
        TAB_LABELS.forEachIndexed { index, label ->
            val isActive = index == activeTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 8.dp)
                    .then(
                        if (isActive) Modifier.border(
                            width = 0.dp,
                            color = Color.Transparent,
                            shape = RoundedCornerShape(0.dp),
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        color = if (isActive) CardColors.textPrimary else CardColors.textDim,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        letterSpacing = 0.02.sp,
                    )
                    if (isActive) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(1.5.dp)
                                .background(
                                    CardColors.tabActiveLine,
                                    RoundedCornerShape(1.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Events tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EventsTab(match: FootballMatch, extras: MatchDetailExtras) {
    Column(modifier = Modifier.background(CardColors.tabBg)) {

        val events = extras.richEvents ?: match.events.map { it.toRich() }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (match.status) {
                        MatchStatus.SCHEDULED -> "Match not yet started"
                        else                 -> "No events recorded"
                    },
                    color = CardColors.textDim,
                    fontSize = 11.sp,
                )
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                events.forEach { event ->
                    EventRow(event = event)
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(CardColors.rowDivider),
                    )
                }
            }
        }

        // Referee line — from FD referees[] (currently ignored, zero-cost)
        if (extras.refereeLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardColors.venueBg)
                    .border(
                        width = 0.5.dp,
                        color = CardColors.sectionDivider,
                        shape = RoundedCornerShape(0.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Referee",
                    color = CardColors.textDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = extras.refereeLabel,
                    color = CardColors.textMuted,
                    fontSize = 10.sp,
                )
            }
        }

        DataSourceLegend(sources = listOf("football-data.org", "api-football"))
    }
}

@Composable
private fun EventRow(event: RichMatchEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventTypeIcon(type = event.type)
        Spacer(Modifier.width(8.dp))
        Text(
            text = event.minute?.let { "${it}'" } ?: "",
            color = CardColors.textDim,
            fontSize = 9.sp,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val playerLine = buildString {
                event.playerName?.let { append(it) }
                if (event.isOwnGoal) append(" (OG)")
            }
            if (playerLine.isNotBlank()) {
                Text(
                    text = playerLine,
                    color = CardColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subLine = buildString {
                if (event.assistName != null) {
                    append("Assist: ${event.assistName}  ·  ")
                }
                append(event.teamName)
            }
            Text(
                text = subLine,
                color = CardColors.textDim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Penalty tag — from OLDB isPenalty (currently ignored)
        if (event.isPenalty) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardColors.tagBg)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(text = "pen", color = CardColors.textDim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun EventTypeIcon(type: EventType) {
    val (bg, fg) = when (type) {
        EventType.GOAL             -> CardColors.goalBg to CardColors.goalText
        EventType.YELLOW_CARD      -> CardColors.yellowBg to CardColors.yellowText
        EventType.RED_CARD         -> CardColors.redBg to CardColors.redText
        EventType.YELLOW_RED_CARD  -> CardColors.redBg to CardColors.redText
        else                       -> CardColors.tagBg to CardColors.textDim
    }
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        FootballEventIcon(type = type, size = 10.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Clubs tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClubsTab(extras: MatchDetailExtras) {
    val home = extras.homeClub
    val away = extras.awayClub

    if (home == null && away == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardColors.tabBg)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Club info loading…",
                color = CardColors.textDim,
                fontSize = 11.sp,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColors.tabBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Home club column
        Column(modifier = Modifier.weight(1f)) {
            ClubField("Club", home?.teamName)
            ClubField("Founded", home?.founded?.toString())
            ClubField("Country", home?.country)
            ClubField("Home ground", home?.stadium)
            ClubField("Capacity", home?.stadiumCapacity?.let { formatCapacity(it) })
        }

        // Divider
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(120.dp)
                .background(CardColors.sectionDivider)
                .align(Alignment.CenterVertically),
        )

        // Away club column
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            ClubField("Club", away?.teamName)
            ClubField("Founded", away?.founded?.toString())
            ClubField("Country", away?.country)
            ClubField("Home ground", away?.stadium)
            ClubField("Capacity", away?.stadiumCapacity?.let { formatCapacity(it) })
        }
    }

    DataSourceLegend(sources = listOf("football-data.org", "TheSportsDB"))
}

@Composable
private fun ClubField(label: String, value: String?) {
    Text(
        text = label.uppercase(),
        color = CardColors.textDim,
        fontSize = 8.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.03.sp,
        modifier = Modifier.padding(bottom = 1.dp),
    )
    Text(
        text = value ?: "–",
        color = if (value != null) CardColors.textSecondary else CardColors.textDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

private fun formatCapacity(cap: Int): String {
    return if (cap >= 1000) {
        val thousands = cap / 1000
        val remainder = (cap % 1000) / 100
        if (remainder > 0) "${thousands},${remainder}00" else "${thousands},000"
    } else cap.toString()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Venues tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VenuesTab(match: FootballMatch, extras: MatchDetailExtras) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColors.tabBg)
            .padding(10.dp),
    ) {
        // Match venue block (from FD venue field)
        if (match.venue != null) {
            Text(
                text = "MATCH VENUE",
                color = CardColors.textDim,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.04.sp,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
            )
            VenueBlock(
                name = match.venue,
                location = extras.homeClub?.stadiumLocation,
                capacity = extras.homeClub?.stadiumCapacity,
                thumbUrl = extras.homeClub?.stadiumThumbUrl,
                sourceNote = "Match venue · football-data.org",
            )
            Spacer(Modifier.height(12.dp))
        }

        // Club home grounds side-by-side
        val home = extras.homeClub
        val away = extras.awayClub
        if (home?.stadium != null || away?.stadium != null) {
            Text(
                text = "CLUB HOME GROUNDS",
                color = CardColors.textDim,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.04.sp,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniVenueBlock(
                    modifier = Modifier.weight(1f),
                    name = home?.stadium ?: "–",
                    location = home?.stadiumLocation,
                    capacity = home?.stadiumCapacity,
                    thumbUrl = home?.stadiumThumbUrl,
                )
                MiniVenueBlock(
                    modifier = Modifier.weight(1f),
                    name = away?.stadium ?: "–",
                    location = away?.stadiumLocation,
                    capacity = away?.stadiumCapacity,
                    thumbUrl = away?.stadiumThumbUrl,
                )
            }
        }

        if (match.venue == null && extras.homeClub?.stadium == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Venue info loading…",
                    color = CardColors.textDim,
                    fontSize = 11.sp,
                )
            }
        }

        DataSourceLegend(sources = listOf("TheSportsDB (strStadiumThumb, intStadiumCapacity)"))
    }
}

@Composable
private fun VenueBlock(
    name: String,
    location: String?,
    capacity: Int?,
    thumbUrl: String?,
    sourceNote: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(0.5.dp, CardColors.cardBorder, RoundedCornerShape(10.dp))
            .background(CardColors.venueBg),
    ) {
        // Stadium image — TheSportsDB strStadiumThumb (/small variant)
        StadiumImage(
            thumbUrl = thumbUrl,
            contentDescription = name,
            height = 90,
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = name,
                color = CardColors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                location,
                capacity?.let { "${formatCapacity(it)} seats" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = CardColors.textDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(CardColors.accentDot),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = sourceNote, color = CardColors.textDim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun MiniVenueBlock(
    modifier: Modifier = Modifier,
    name: String,
    location: String?,
    capacity: Int?,
    thumbUrl: String?,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, CardColors.cardBorder, RoundedCornerShape(8.dp))
            .background(CardColors.venueBg),
    ) {
        StadiumImage(
            thumbUrl = thumbUrl,
            contentDescription = name,
            height = 54,
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = name,
                color = CardColors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                location,
                capacity?.let { formatCapacity(it) },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    color = CardColors.textDim,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StadiumImage(
    thumbUrl: String?,
    contentDescription: String,
    height: Int,
) {
    val context = LocalContext.current
    if (thumbUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumbUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🏟", fontSize = (height / 3).sp, color = CardColors.textDim)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared primitives
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(match: FootballMatch, extras: MatchDetailExtras) {
    val duration = extras.scoreDuration ?: "REGULAR"
    val (bg, fg, label) = when {
        match.status == MatchStatus.LIVE -> Triple(
            CardColors.liveBg, CardColors.liveText,
            "● " + (match.minute?.let { "${it}'" } ?: "LIVE"),
        )
        match.status == MatchStatus.HALF_TIME -> Triple(
            CardColors.htBg, CardColors.htText, "Half Time",
        )
        match.status == MatchStatus.FINISHED && duration == "EXTRA_TIME" -> Triple(
            CardColors.etBg, CardColors.etText, "AET",
        )
        match.status == MatchStatus.FINISHED && duration == "PENALTY_SHOOTOUT" -> Triple(
            CardColors.etBg, CardColors.etText, "Pens",
        )
        match.status == MatchStatus.FINISHED -> Triple(
            CardColors.ftBg, CardColors.ftText, "Full Time",
        )
        else -> Triple(
            CardColors.schedBg, CardColors.schedText, match.kickoffTime(),
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TeamCrest(url: String?, name: String, size: Int) {
    val context = LocalContext.current
    if (url != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(CardColors.crestBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = CardColors.textMuted,
                fontSize = (size * 0.38f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DataSourceLegend(sources: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColors.tabBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sources.forEach { source ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(CardColors.accentDot),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = source, color = CardColors.textDim, fontSize = 8.sp)
            }
        }
    }
}