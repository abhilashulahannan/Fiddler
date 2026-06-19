package com.example.fiddler.subapps.Fidland.phs3.football

/**
 * FootballScheduleEngine — converts a raw list of today's kick-off times into
 * a [DailyPollPlan] that tells [FootballRepository] exactly when to fire its
 * 100 api-football calls.
 *
 * ── Concepts ──────────────────────────────────────────────────────────────────
 *
 * MATCH DURATION
 *   Each match is assumed to last [MATCH_DURATION_MS] (90 min play + 23 min
 *   buffer = 113 min total). Overlapping matches produce a merged active window.
 *
 * ACTIVE WINDOW
 *   A continuous time range [startMs, endMs] during which at least one match
 *   is in play. Gaps between windows are "transitional" — OpenLigaDB covers
 *   those without burning AF budget.
 *
 * DAILY POLL PLAN
 *   The 100 AF calls are split evenly across all windows (rounded down; any
 *   remainder goes to the last window). Within each window the polls are
 *   spaced evenly across the window's duration. If a window is already past
 *   when the plan is (re)computed, its allocation is scaled down proportionally.
 *
 * RECONCILIATION
 *   FD re-polls every minute so the schedule can change (postponements, added
 *   fixtures). [DailyPollPlan.reconcileWith] is called on every FD poll. It:
 *     1. Recomputes windows from the latest schedule.
 *     2. Removes already-fired poll timestamps.
 *     3. Redistributes the remaining budget across remaining window time.
 *
 * SAFETY MARGIN
 *   We target [AF_DAILY_BUDGET] = 95 calls (not 100) to leave headroom for
 *   manual testing, retries, and rounding.
 */
object FootballScheduleEngine {

    /** Assumed match duration including stoppage time + short buffer (90+23 min). */
    const val MATCH_DURATION_MS: Long = 113L * 60_000L

    /**
     * Max api-football calls the schedule engine will plan per day.
     * Matches the free tier hard limit exactly (100). The actual enforcement
     * gate is in [AfRequestLog] (daily cap) and [ApiFootballSource] (90 s spacing),
     * so the schedule engine no longer needs a safety margin here.
     */
    const val AF_DAILY_BUDGET: Int = 100

    /**
     * Minimum gap enforced between any two planned AF polls (and between now and
     * the next generated poll during reconcile). Prevents reconcile() from
     * producing timestamps that are immediately due on the next 15-second AF check.
     */
    const val MIN_POLL_SPACING_MS: Long = 3 * 60_000L  // 3 minutes

    // ─────────────────────────────────────────────────────────────────────────
    //  Public data classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A continuous block of time during which at least one match is active.
     *
     * @param index     Position in today's ordered window list (0-based).
     * @param startMs   Epoch ms when the first match in this block kicks off.
     * @param endMs     Epoch ms when the last match in this block is assumed to finish.
     * @param allotment Number of AF calls assigned to this window.
     */
    data class ActiveWindow(
        val index: Int,
        val startMs: Long,
        val endMs: Long,
        val allotment: Int,
    ) {
        val durationMs: Long get() = endMs - startMs
        fun contains(nowMs: Long) = nowMs in startMs..endMs
        fun isInFuture(nowMs: Long) = startMs > nowMs
        fun isPast(nowMs: Long) = endMs < nowMs
        fun remainingMs(nowMs: Long) = (endMs - nowMs).coerceAtLeast(0L)
    }

    /**
     * A single api-football call, scheduled for epoch ms [atMs].
     *
     * [windowIndex] links the poll back to its [ActiveWindow] for logging.
     * [fired] is set to true by [FootballRepository] once the call is made
     * (used to protect against double-firing after a reconcile).
     */
    data class PlannedPoll(
        val atMs: Long,
        val windowIndex: Int,
        var fired: Boolean = false,
    )

    /**
     * The full computed schedule for one UTC calendar day.
     *
     * @param dayUtcDate  "yyyy-MM-dd" string that identifies which day this plan covers.
     * @param windows     Ordered list of [ActiveWindow]s derived from today's schedule.
     * @param polls       Full ordered list of [PlannedPoll]s — the moments when the
     *                    [FootballRepository] AF loop should fire a request.
     */
    data class DailyPollPlan(
        val dayUtcDate: String,
        val windows: List<ActiveWindow>,
        val polls: List<PlannedPoll>,
    ) {
        /**
         * True if this plan covers at least one active window that has not yet ended.
         */
        fun hasRemainingWork(nowMs: Long = System.currentTimeMillis()): Boolean =
            windows.any { !it.isPast(nowMs) }

        /**
         * Reconcile this existing plan with [fresh] (just recomputed from FD's
         * latest schedule). Called on every FD poll.
         *
         * Strategy:
         * 1. Carry over already-fired poll count → compute remaining budget.
         * 2. Use fresh window layout (respects postponements / new fixtures).
         * 3. Remove any polls already marked fired.
         * 4. Distribute remaining budget across unfired window time.
         * 5. Discard poll slots that have already passed (to avoid gaps becoming
         *    immediate-fire storms after a plan update).
         *
         * @param fresh    Freshly computed plan (as if starting from scratch today).
         * @param nowMs    Current epoch ms (for "how much time is left?" arithmetic).
         */
        fun reconcileWith(fresh: DailyPollPlan, nowMs: Long): DailyPollPlan {
            if (fresh.dayUtcDate != dayUtcDate) return fresh   // new day — full reset

            val alreadyFired = polls.count { it.fired }
            val remainingBudget = (AF_DAILY_BUDGET - alreadyFired).coerceAtLeast(0)

            if (remainingBudget == 0) {
                // Budget exhausted — keep window list updated but no new polls.
                return fresh.copy(polls = emptyList())
            }

            // KEY FIX: keep all unfired polls that are still in the future.
            // Only regenerate slots for windows that are genuinely new (added by
            // a postponement reversal or fixture addition) and have no existing coverage.
            // This prevents reconcile() from churning out fresh near-future timestamps
            // on every FD poll and re-triggering the AF loop spuriously.
            val survivingPolls = polls.filter { !it.fired && it.atMs > nowMs + MIN_POLL_SPACING_MS }

            // Tally surviving slots per window so we can top up windows that are
            // under-covered (e.g. a new fixture added a window that has no polls yet).
            val survivingByWindow = survivingPolls.groupBy { it.windowIndex }
            val perWindow = remainingBudget / fresh.windows.size.coerceAtLeast(1)

            val topUpPolls = mutableListOf<PlannedPoll>()
            fresh.windows.forEachIndexed { i, window ->
                if (window.isPast(nowMs)) return@forEachIndexed
                val existing = survivingByWindow[i]?.size ?: 0
                val needed   = (perWindow - existing).coerceAtLeast(0)
                if (needed == 0) return@forEachIndexed

                // Only schedule top-up polls in window time not yet covered.
                // Push effectiveStart past the last surviving poll in this window
                // (or nowMs if none) and require MIN_POLL_SPACING_MS clearance.
                val lastSurviving = survivingByWindow[i]?.maxOfOrNull { it.atMs }
                val effectiveStart = maxOf(
                    window.startMs,
                    nowMs + MIN_POLL_SPACING_MS,
                    (lastSurviving ?: 0L) + MIN_POLL_SPACING_MS,
                )
                if (effectiveStart >= window.endMs) return@forEachIndexed

                val intervals = needed + 1
                val step = (window.endMs - effectiveStart) / intervals
                if (step < MIN_POLL_SPACING_MS) return@forEachIndexed  // window too short to fit more

                for (j in 1..needed) {
                    val atMs = effectiveStart + step * j
                    if (atMs <= nowMs) continue
                    topUpPolls += PlannedPoll(atMs = atMs, windowIndex = i)
                }
            }

            val mergedPolls = (survivingPolls + topUpPolls).sortedBy { it.atMs }
            return fresh.copy(polls = mergedPolls)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core computation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build a fresh [DailyPollPlan] from today's kick-off timestamps.
     *
     * @param dayUtcDate  "yyyy-MM-dd" string for today.
     * @param kickoffsMs  Epoch ms timestamps of every match kicking off today.
     * @param nowMs       Current epoch ms (default = System.currentTimeMillis()).
     */
    fun computeForToday(
        dayUtcDate: String,
        kickoffsMs: List<Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): DailyPollPlan {
        if (kickoffsMs.isEmpty()) {
            return DailyPollPlan(dayUtcDate, emptyList(), emptyList())
        }

        val windows = buildActiveWindows(kickoffsMs)

        // Assign call budget: split evenly across windows.
        val perWindow = AF_DAILY_BUDGET / windows.size
        val remainder = AF_DAILY_BUDGET % windows.size

        val windowsWithAllotment = windows.mapIndexed { i, w ->
            val extra = if (i == windows.lastIndex) remainder else 0
            w.copy(allotment = perWindow + extra)
        }

        val polls = distributePollsAcrossWindows(
            windows = windowsWithAllotment,
            totalBudget = AF_DAILY_BUDGET,
            nowMs = nowMs,
            skipPast = false,   // fresh plan — include all, even if now > window start
        )

        return DailyPollPlan(dayUtcDate, windowsWithAllotment, polls)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convert a raw list of kick-off times into merged [ActiveWindow]s.
     *
     * Algorithm:
     *   1. Sort kick-offs ascending.
     *   2. Each kick-off starts a candidate window [kickoff, kickoff + MATCH_DURATION_MS].
     *   3. If the candidate's start overlaps the current open window, extend the
     *      open window's end (if the new end is later). Otherwise close the open
     *      window and start a new one.
     */
    private fun buildActiveWindows(kickoffsMs: List<Long>): List<ActiveWindow> {
        val sorted = kickoffsMs.sorted()
        val windows = mutableListOf<ActiveWindow>()

        var winStart = sorted.first()
        var winEnd   = winStart + MATCH_DURATION_MS

        for (i in 1 until sorted.size) {
            val ko = sorted[i]
            val koEnd = ko + MATCH_DURATION_MS

            if (ko <= winEnd) {
                // Overlaps — extend current window if this match ends later.
                if (koEnd > winEnd) winEnd = koEnd
            } else {
                // Gap — close current window and start a new one.
                windows += ActiveWindow(
                    index = windows.size,
                    startMs = winStart,
                    endMs = winEnd,
                    allotment = 0,  // filled in after
                )
                winStart = ko
                winEnd   = koEnd
            }
        }
        // Close the final open window.
        windows += ActiveWindow(
            index = windows.size,
            startMs = winStart,
            endMs = winEnd,
            allotment = 0,
        )

        return windows
    }

    /**
     * Turn a list of [ActiveWindow]s (each with a non-zero [allotment]) into
     * a flat, time-ordered list of [PlannedPoll]s.
     *
     * @param windows       Windows already carrying their allotment.
     * @param totalBudget   Total calls to distribute (used when skipPast trims).
     * @param nowMs         Current epoch ms.
     * @param skipPast      If true, only place polls in future time — polls
     *                      before nowMs are omitted (reconcile mode). If false,
     *                      distribute across the entire window duration (initial
     *                      plan mode; calls for windows already underway are
     *                      placed starting at nowMs).
     */
    private fun distributePollsAcrossWindows(
        windows: List<ActiveWindow>,
        totalBudget: Int,
        nowMs: Long,
        skipPast: Boolean,
    ): List<PlannedPoll> {
        if (windows.isEmpty() || totalBudget <= 0) return emptyList()

        // Recompute per-window allotments now that we know remaining budget.
        val perWindow = totalBudget / windows.size
        val remainder = totalBudget % windows.size

        val polls = mutableListOf<PlannedPoll>()

        windows.forEachIndexed { i, window ->
            val allotment = perWindow + (if (i == windows.lastIndex) remainder else 0)
            if (allotment <= 0) return@forEachIndexed

            val effectiveStart = if (skipPast) maxOf(window.startMs, nowMs) else window.startMs
            val effectiveEnd   = window.endMs

            if (effectiveStart >= effectiveEnd) return@forEachIndexed  // window fully past

            // Space [allotment] polls evenly across [effectiveStart, effectiveEnd].
            // We use (allotment + 1) intervals so the first poll isn't at the very
            // start (gives FD a moment to register the kick-off) and the last isn't
            // at the very end (the match is probably done by then).
            val intervals = allotment + 1
            val step = (effectiveEnd - effectiveStart) / intervals

            for (j in 1..allotment) {
                val atMs = effectiveStart + step * j
                if (atMs < nowMs && skipPast) continue  // safety guard
                polls += PlannedPoll(atMs = atMs, windowIndex = i)
            }
        }

        return polls.sortedBy { it.atMs }
    }

    /**
     * Quick helper — is [nowMs] inside any of today's active windows?
     * Used by [FootballRepository] to decide whether to add OLDB data or
     * rely purely on AF during its active windows.
     */
    fun isInActiveWindow(plan: DailyPollPlan?, nowMs: Long = System.currentTimeMillis()): Boolean =
        plan?.windows?.any { it.contains(nowMs) } ?: false
}