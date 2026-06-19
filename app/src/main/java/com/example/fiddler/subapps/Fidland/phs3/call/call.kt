package com.example.fiddler.subapps.Fidland.phs3.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import kotlinx.coroutines.delay

// ── Layout constants ──────────────────────────────────────────────────────────

private val CALL_TEXT_COLUMN_WIDTH: Dp = 72.dp

/** Rotation interval for missed-call cycling in the indicator (ms). */
private const val MISSED_CALL_ROTATE_INTERVAL_MS = 4_000L

/** Timer tick interval for the active-call duration counter (ms). */
private const val CALL_TIMER_TICK_MS = 1_000L

// ─────────────────────────────────────────────────────────────────────────────
//  ACTIVE CALL HANDLER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Phs3 module — Active Call.
 *
 * Qualifies when the device has an active incoming or outgoing call
 * (via TelecomManager / InCallService). Deactivates once the call ends.
 *
 * Layout (State 3 indicator):
 *   a  — [CallPhoneIcon] in green.  TODO: replace icon with Lottie animation.
 *   b  — Live call-duration timer (MM:SS / H:MM:SS). Shown only once
 *          [ActiveCallInfo.connectionState] transitions to ACTIVE; blank while
 *          RINGING or CONNECTING.
 *   c  — Two-line text: contact name (bold, larger) / phone number (dim, smaller).
 *
 * State 5 ([ControlsPanel]):
 *   Action buttons arranged in a grid:
 *     - End call   (red)
 *     - Mute       (toggle, white / dim)
 *     - Speaker    (toggle, white / dim)
 *     - Record     (toggle, white / dim — check local-recording law before wiring)
 *     - Add call
 *     - Keypad     (placeholder)
 *
 *   ── FUTURE: Recent Messages strip ────────────────────────────────────────
 *   Below the action grid, a horizontally-scrolling strip that surfaces the
 *   most recent message(s) from the current caller across multiple channels:
 *     • SMS / RCS  — via ContentResolver(Telephony.Sms / Telephony.Mms)
 *     • WhatsApp   — via notification listener or WhatsApp Business API
 *     • Email      — via ContentResolver (Gmail / generic mail provider)
 *   Each item shows: channel icon, snippet, timestamp.
 *   Tapping opens the relevant app / thread directly.
 *   No implementation yet — add a TODO placeholder UI for the slot.
 *   ─────────────────────────────────────────────────────────────────────────
 *
 * @param callInfo    Current active-call snapshot. Re-supply as state updates
 *                     (mute toggled, speaker toggled, call accepted, etc.).
 * @param onEndCall   Invoked when the user taps "End call".
 * @param onMute      Invoked when the user toggles mute.
 * @param onSpeaker   Invoked when the user toggles speaker.
 * @param onRecord    Invoked when the user toggles recording.
 * @param onAddCall   Invoked when the user taps "Add call".
 * @param onKeypad    Invoked when the user taps "Keypad".
 */
class ActiveCallPhs3Handler(
    private val callInfo: ActiveCallInfo,
    private val onEndCall: () -> Unit = {},
    private val onMute: () -> Unit = {},
    private val onSpeaker: () -> Unit = {},
    private val onRecord: () -> Unit = {},
    private val onAddCall: () -> Unit = {},
    private val onKeypad: () -> Unit = {},
) : Phs3Handler {

    override val label: String = "ActiveCall"

    @Composable
    override fun Indicator() {
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

        // Tick every second to update the duration timer.
        LaunchedEffect(Unit) {
            while (true) {
                delay(CALL_TIMER_TICK_MS)
                nowMs = System.currentTimeMillis()
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── Location A: green phone icon ──────────────────────────────
            // TODO: replace with Lottie animation when assets are ready.
            CallPhoneIcon(missed = false, size = 16.dp)

            // ── Location B: call duration timer ──────────────────────────
            // Blank while ringing; starts once the call is accepted (talkStartMs set).
            val timerText = if (callInfo.connectionState == CallConnectionState.ACTIVE) {
                formatDuration(callInfo.elapsedMs(nowMs))
            } else {
                // Show direction hint while ringing.
                when (callInfo.direction) {
                    CallDirection.INCOMING -> "Incoming…"
                    CallDirection.OUTGOING -> "Calling…"
                }
            }

            Text(
                text = timerText,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.width(38.dp),
            )

            // ── Location C: name (top, bigger) + number (bottom, smaller) ─
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(CALL_TEXT_COLUMN_WIDTH),
            ) {
                Text(
                    text = callInfo.displayName ?: callInfo.phoneNumber,
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (callInfo.displayName != null) {
                    Text(
                        text = callInfo.phoneNumber,
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
    }

    @Composable
    override fun State5Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Caller info at the top of State 5.
            Text(
                text = callInfo.displayName ?: callInfo.phoneNumber,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            if (callInfo.displayName != null) {
                Text(
                    text = callInfo.phoneNumber,
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Action button grid (2 columns) ──────────────────────────
            val buttonItems: List<CallActionButton> = listOf(
                CallActionButton(
                    label = "End",
                    activeColor = Color(0xFFEF4444),
                    isActive = true, // always "active" state (red, always on)
                    onClick = onEndCall
                ),
                CallActionButton(
                    label = if (callInfo.isMuted) "Unmute" else "Mute",
                    activeColor = Color.White,
                    isActive = callInfo.isMuted,
                    onClick = onMute
                ),
                CallActionButton(
                    label = "Speaker",
                    activeColor = Color.White,
                    isActive = callInfo.isSpeakerOn,
                    onClick = onSpeaker
                ),
                CallActionButton(
                    label = "Record",
                    activeColor = Color(0xFFFF6B6B),
                    isActive = callInfo.isRecording,
                    onClick = onRecord
                ),
                CallActionButton(
                    label = "Add call",
                    activeColor = Color.White,
                    isActive = false,
                    onClick = onAddCall
                ),
                CallActionButton(
                    label = "Keypad",
                    activeColor = Color.White,
                    isActive = false,
                    onClick = onKeypad
                ),
            )

            // 2-column grid of action buttons.
            buttonItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { btn ->
                        ActiveCallActionButton(
                            button = btn,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad the last row if odd number of buttons.
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // ── FUTURE: Recent Messages strip ──────────────────────────────
            // TODO: Add a horizontally-scrolling strip here that shows the
            //        most recent message(s) from the active caller across:
            //          • SMS / RCS   — ContentResolver(Telephony.Sms / Mms)
            //          • WhatsApp    — notification listener / WA Business API
            //          • Email       — ContentResolver (Gmail / mail provider)
            //        Each item: channel icon | snippet text | timestamp
            //        Tap → deep-link into the relevant app/thread.
            //        No code yet — this block is a placeholder for that feature.
            // ──────────────────────────────────────────────────────────────
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MISSED CALL HANDLER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Phs3 module — Missed Calls.
 *
 * Qualifies when at least one missed call has been received in the last 24 hours.
 * Deactivates once all missed calls are acknowledged / cleared.
 *
 * Layout (State 3 indicator):
 *   a  — [CallPhoneIcon] in red.  TODO: replace with Lottie animation.
 *   b  — Missed-call count from the caller currently shown in Location C
 *          (e.g. "x3"). Hidden if count == 1.
 *   c  — Two-line text: caller name (top) / phone number (bottom).
 *          Cycles through unique callers at [MISSED_CALL_ROTATE_INTERVAL_MS].
 *
 * State 5 ([ControlsPanel]):
 *   Full chronological list of missed-call entries across all callers —
 *   newest first. Each row: caller name / number, time of call, "Call back"
 *   tap action.
 *
 * @param missedCalls  List of grouped missed-call infos, one entry per unique
 *                      caller, sorted by most-recent call descending. Each
 *                      [MissedCallInfo.entries] lists the individual calls for
 *                      the State 5 flat list.
 * @param onCallBack   Invoked with the phone number when the user taps
 *                      "Call back" in State 5.
 */
class MissedCallPhs3Handler(
    private val missedCalls: List<MissedCallInfo>,
    private val onCallBack: (phoneNumber: String) -> Unit = {},
) : Phs3Handler {

    override val label: String = "MissedCall"

    @Composable
    override fun Indicator() {
        // Index into [missedCalls] that is currently displayed in location C.
        // Rotates on a fixed interval so each unique caller gets a turn.
        var currentIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(missedCalls.size) {
            while (missedCalls.size > 1) {
                delay(MISSED_CALL_ROTATE_INTERVAL_MS)
                currentIndex = (currentIndex + 1) % missedCalls.size
            }
        }

        val current = missedCalls.getOrNull(currentIndex) ?: return

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── Location A: red phone icon ────────────────────────────────
            // TODO: replace with Lottie animation when assets are ready.
            CallPhoneIcon(missed = true, size = 16.dp)

            // ── Location B: missed-call count badge for the current caller ─
            // Hidden when count is 1 (no badge needed for a single missed call).
            val badge = missedCountBadge(current.count)
            Text(
                text = badge,
                color = Color(0xFFEF4444),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.width(20.dp),
            )

            // ── Location C: name (top) + number (bottom) ──────────────────
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(CALL_TEXT_COLUMN_WIDTH),
            ) {
                Text(
                    text = current.displayName ?: current.phoneNumber,
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (current.displayName != null) {
                    Text(
                        text = current.phoneNumber,
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
    }

    @Composable
    override fun State5Content() {
        // Flatten all individual entries across all callers, newest first.
        val allEntries: List<Pair<MissedCallInfo, MissedCallEntry>> = remember(missedCalls) {
            missedCalls
                .flatMap { info -> info.entries.map { entry -> info to entry } }
                .sortedByDescending { (_, entry) -> entry.timestampMs }
        }

        if (allEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No missed calls",
                    color = Color(0xFF888888),
                    fontSize = 13.sp
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp,
                bottom = 12.dp
            )
        ) {
            item {
                Text(
                    text = "Missed calls",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(allEntries) { (info, entry) ->
                MissedCallRow(
                    info = info,
                    entry = entry,
                    onCallBack = onCallBack
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PRIVATE UI HELPERS
// ─────────────────────────────────────────────────────────────────────────────

/** Data for a single action button in the ActiveCall State 5 grid. */
private data class CallActionButton(
    val label: String,
    val activeColor: Color,
    val isActive: Boolean,
    val onClick: () -> Unit,
)

/** A pill-shaped action button used in the ActiveCall [ControlsPanel]. */
@Composable
private fun ActiveCallActionButton(
    button: CallActionButton,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (button.isActive) button.activeColor.copy(alpha = 0.15f)
    else Color(0xFF1A1A1A)
    val textColor = if (button.isActive) button.activeColor else Color(0xFF888888)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = button.onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Text(
            text = button.label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * A single row in the MissedCall State 5 list.
 * Shows: red dot indicator | name/number | timestamp | "Call back" chip.
 */
@Composable
private fun MissedCallRow(
    info: MissedCallInfo,
    entry: MissedCallEntry,
    onCallBack: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Red missed-call dot.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
        )

        // Name + number.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName ?: entry.phoneNumber,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.displayName != null) {
                Text(
                    text = entry.phoneNumber,
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        // Timestamp (e.g. "9:15 AM").
        Text(
            text = formatClockTime(entry.timestampMs),
            color = Color(0xFF666666),
            fontSize = 10.sp,
        )

        // "Call back" chip.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                .clickable { onCallBack(entry.phoneNumber) }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Call back",
                color = Color(0xFF22C55E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}