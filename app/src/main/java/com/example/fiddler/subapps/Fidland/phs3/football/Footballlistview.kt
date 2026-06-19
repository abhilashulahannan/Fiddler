package com.example.fiddler.subapps.Fidland.phs3.football

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.TopicPage

// ─────────────────────────────────────────────────────────────────────────────
//  Colors (local — mirrors MatchDetailCard.kt's CardColors)
// ─────────────────────────────────────────────────────────────────────────────

private object ListColors {
    val bg          = Color(0xFF0E0E0E)
    val headerText  = Color(0xFFFFFFFF)
    val compName    = Color(0xFF999999)
    val compMeta    = Color(0xFF555555)
    val compDivider = Color(0xFF1E1E1E)
    val compLogoBg  = Color(0xFF1C1C1C)
    val compLogoBorder = Color(0xFF2A2A2A)
    val emptyText   = Color(0xFF555555)
    val liveText    = Color(0xFF4ADE80)
    val liveBg      = Color(0xFF0D2416)
}

// ─────────────────────────────────────────────────────────────────────────────
//  FootballTopicCompose — replaces the original in Footballtopic.kt
//
//  Key change from the original: the list view is now card-based (in-place
//  expand/collapse via MatchDetailCard), so the separate FootballDetailView
//  pane and its AnimatedContent navigation are no longer needed. The detail
//  content lives inside each card. The class is otherwise a drop-in replacement
//  — same constructor, same TopicPage contract, same repo reference.
// ─────────────────────────────────────────────────────────────────────────────

class FootballTopicCompose(
    context: Context,
    private val repo: FootballRepository,
) : TopicPage(context) {

    @Composable
    override fun Content() {
        val matches by repo.matchesFlow.collectAsState()

        // Build extras map from TSDB's in-memory badge cache.
        // TheSportsDbSource.cachedBadge() is synchronous — no coroutine needed.
        // Club profile fields (founded, stadium…) require a separate
        // cachedClubProfile() method added to TheSportsDbSource alongside this
        // file. Until that's wired, extras degrade gracefully to empty Clubs /
        // Venues tabs (MatchDetailCard handles null ClubProfile cleanly).
        val extrasMap by remember(matches) {
            derivedStateOf {
                matches.associate { match ->
                    match.id to buildExtras(match, repo)
                }
            }
        }

        FootballListView(
            matches = matches,
            extrasMap = extrasMap,
        )
    }

    override fun onSwipeLeft()  {}
    override fun onSwipeRight() {}
}

// ─────────────────────────────────────────────────────────────────────────────
//  Extras builder
//  Pulls whatever is already in the TSDB cache and packages it into
//  MatchDetailExtras. Expand this function as TheSportsDbSource grows to
//  expose more fields (stadium, capacity, etc.).
// ─────────────────────────────────────────────────────────────────────────────

private fun buildExtras(match: FootballMatch, repo: FootballRepository): MatchDetailExtras {
    // ClubProfile — populated once TheSportsDbSource.cachedClubProfile() exists.
    // Until then these are null and the card shows "loading…" placeholders.
    val homeClub: ClubProfile? = repo.sportsDb.cachedClubProfile(match.homeTeam)
    val awayClub: ClubProfile? = repo.sportsDb.cachedClubProfile(match.awayTeam)

    // Rich events — populated once OLDB parser exposes isPenalty/isOwnGoal/assistName.
    // Until then falls back to match.events.map { it.toRich() } inside MatchDetailCard.
    val richEvents: List<RichMatchEvent>? = null

    return MatchDetailExtras(
        homeClub    = homeClub,
        awayClub    = awayClub,
        richEvents  = richEvents,
        // refereeLabel, htScore, scoreDuration, homeTla, awayTla:
        // add these once parseFdMatches stores them on FootballMatch.
        refereeLabel  = null,
        htScore       = null,
        scoreDuration = null,
        homeTla       = null,
        awayTla       = null,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  FootballListView
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FootballListView(
    matches: List<FootballMatch>,
    extrasMap: Map<String, MatchDetailExtras> = emptyMap(),
) {
    if (matches.isEmpty()) {
        EmptyState()
        return
    }

    // Group by competition, preserving the order matches arrive in from the
    // repo (live first, then scheduled, then finished within each group).
    // groupBy preserves insertion order in Kotlin, so competition order tracks
    // whichever match was first seen per competition — consistent with the
    // repo's isActive() desc + kickoff asc ordering.
    val grouped: Map<Competition, List<FootballMatch>> = remember(matches) {
        matches.groupBy { it.competition }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ListColors.bg),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        grouped.forEach { (competition, compMatches) ->

            // Competition header
            item(key = "header_${competition.id}") {
                CompetitionHeader(competition = competition, matchCount = compMatches.size)
            }

            // Match cards — each expands in-place, no navigation
            items(
                items = compMatches,
                key   = { it.id },
            ) { match ->
                MatchDetailCard(
                    match  = match,
                    extras = extrasMap[match.id] ?: MatchDetailExtras(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bottom gap after the last card in each competition section
            item(key = "gap_${competition.id}") {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Competition header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompetitionHeader(competition: Competition, matchCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Competition logo placeholder — replace with AsyncImage once TSDB
        // exposes a competition badge URL (it currently only exposes team badges).
        CompetitionLogo(competition = competition)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = competition.displayName,
                color = ListColors.compName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.02.sp,
            )
        }

        // Live indicator — shown when any match in this group is active
        val hasLive = matchCount > 0 // caller already filtered; adjust if needed
        Text(
            text = "$matchCount match${if (matchCount == 1) "" else "es"}",
            color = ListColors.compMeta,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CompetitionLogo(competition: Competition) {
    // One-letter monogram in a small rounded square — consistent with the HTML
    // mockup's comp-logo treatment. Swap for an AsyncImage when TSDB comp
    // badges become available.
    val (bg, textColor) = competitionAccent(competition.id)
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = competition.displayName.first().uppercaseChar().toString(),
            color = textColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Per-competition accent colors for the header logo monogram.
 * Returns Pair(backgroundColor, textColor).
 */
private fun competitionAccent(id: String): Pair<Color, Color> = when (id) {
    "CL"  -> Color(0xFF0C1A2E) to Color(0xFF60A5FA)   // UCL — blue
    "PL"  -> Color(0xFF1A0A1A) to Color(0xFFA855F7)   // Premier League — purple
    "BL1" -> Color(0xFF1A0A00) to Color(0xFFF59E0B)   // Bundesliga — amber
    "PD"  -> Color(0xFF1A0A00) to Color(0xFFF97316)   // La Liga — orange
    "SA"  -> Color(0xFF1A0A00) to Color(0xFFEF4444)   // Serie A — red
    "FL1" -> Color(0xFF0A0A1A) to Color(0xFF818CF8)   // Ligue 1 — indigo
    "WC"  -> Color(0xFF0A1A0A) to Color(0xFF4ADE80)   // World Cup — green
    "EC"  -> Color(0xFF00001A) to Color(0xFF93C5FD)   // Euros — sky blue
    else  -> Color(0xFF1C1C1C) to Color(0xFF888888)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ListColors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No matches today",
                color = ListColors.emptyText,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Check back before kick-off",
                color = Color(0xFF333333),
                fontSize = 11.sp,
            )
        }
    }
}