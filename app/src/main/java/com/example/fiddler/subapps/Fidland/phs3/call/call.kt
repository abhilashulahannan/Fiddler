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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.NotificationListenerService
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerContext
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerIndicator
import com.example.fiddler.subapps.Fidland.phs3.shared.EqualizerMode
import com.example.fiddler.ui.icons.MonoLottieIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

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
 *   a  — [CallPhoneIcon] in green (Lottie, res/raw/call_call.json).
 *   b  — Live call-duration timer (MM:SS / H:MM:SS). Shown only once
 *          [ActiveCallInfo.connectionState] transitions to ACTIVE; blank while
 *          RINGING or CONNECTING.
 *   c  — Two-line text: contact name (bold, larger) / phone number (dim, smaller).
 *
 * State 5 ([ControlsPanel]) — redesigned, still a compact 280×150.dp strip
 * (see [state5HeightOverride]), not a full in-call screen:
 *   Row 1 — header: circular avatar (initials, or a person glyph if the
 *           caller couldn't be resolved) · name/number · connection status
 *           on the right (live "MM:SS" duration once ACTIVE, "On hold", or
 *           "Incoming…"/"Calling…" while RINGING).
 *   Row 2 — [EqualizerIndicator] using shared/Equalizer.kt's tuned CALL
 *           preset: a Dynamic-Island-style bar visualizer that animates
 *           while the call is ACTIVE and unmuted, and stills to a flat
 *           dim row the instant the mic is muted, the line's on hold, or
 *           it's still ringing. Simulated, not Live — real per-party voice
 *           audio isn't reachable via AudioVisualizerEngine's session-0
 *           Visualizer tap (that's the app-visible mixer, which telephony
 *           voice audio bypasses on stock Android) — see that class's kdoc.
 *   Row 3 — single row of 6 circular icon buttons: Mute, Speaker, Record,
 *           Add call, Keypad, End. Toggle buttons (Mute/Speaker/Record)
 *           fill solid with their colour when on; End is always solid red.
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

    // Header + equalizer + 6-button control row need more vertical room
    // than the shared IslandConfig.STATE5_HEIGHT default (115.dp) allows.
    override val state5HeightOverride: Dp = 150.dp

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
        // Independent tick from Indicator()'s — Indicator may not even be
        // composed while State5 is open (they're mutually exclusive pill
        // phases), so this needs its own LaunchedEffect to keep the big
        // duration readout live.
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(CALL_TIMER_TICK_MS)
                nowMs = System.currentTimeMillis()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Header: avatar · name/number · live status ─────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CallerAvatar(displayName = callInfo.displayName, size = 34.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callInfo.displayName ?: callInfo.phoneNumber,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (callInfo.displayName != null) {
                        Text(
                            text = callInfo.phoneNumber,
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val statusText = when (callInfo.connectionState) {
                    CallConnectionState.ACTIVE -> formatDuration(callInfo.elapsedMs(nowMs))
                    CallConnectionState.ON_HOLD -> "On hold"
                    CallConnectionState.RINGING -> when (callInfo.direction) {
                        CallDirection.INCOMING -> "Incoming…"
                        CallDirection.OUTGOING -> "Calling…"
                    }
                }
                Text(
                    text = statusText,
                    color = if (callInfo.connectionState == CallConnectionState.ACTIVE)
                        Color(0xFF4ADE80) else Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            // ── Live equalizer — Dynamic-Island-style voice activity bars ──
            // Animates while ACTIVE + unmuted; stills the instant the mic is
            // muted or the call is on hold/ringing, so it stays an honest
            // reflection of call state instead of a decorative loop that
            // keeps moving over a muted or ringing call.
            val eqLive = callInfo.connectionState == CallConnectionState.ACTIVE && !callInfo.isMuted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                EqualizerIndicator(
                    mode = EqualizerMode.Simulated(
                        if (eqLive) EqualizerContext.CALL else EqualizerContext.RECORD
                    ),
                    barCount = 9,
                    barWidth = 3.dp,
                    barSpacing = 3.dp,
                    maxHeight = 16.dp,
                    minHeight = 3.dp,
                    color = if (eqLive) Color(0xFF4ADE80) else Color(0xFF3A3A3A),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Control row: 6 circular icon buttons ────────────────────────
            val controls: List<CallControlButton> = listOf(
                CallControlButton(
                    label = if (callInfo.isMuted) "Unmute" else "Mute",
                    icon = if (callInfo.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    activeColor = Color.White,
                    isActive = callInfo.isMuted,
                    onClick = onMute,
                ),
                CallControlButton(
                    label = "Speaker",
                    icon = if (callInfo.isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    activeColor = Color.White,
                    isActive = callInfo.isSpeakerOn,
                    onClick = onSpeaker,
                ),
                CallControlButton(
                    label = "Record",
                    lottieRawRes = R.raw.recording,
                    activeColor = Color(0xFFFF6B6B),
                    isActive = callInfo.isRecording,
                    onClick = onRecord,
                ),
                CallControlButton(
                    label = "Add call",
                    icon = Icons.Filled.PersonAdd,
                    activeColor = Color.White,
                    isActive = false,
                    onClick = onAddCall,
                ),
                CallControlButton(
                    label = "Keypad",
                    icon = Icons.Filled.Dialpad,
                    activeColor = Color.White,
                    isActive = false,
                    onClick = onKeypad,
                ),
                CallControlButton(
                    label = "End",
                    icon = Icons.Filled.CallEnd,
                    activeColor = Color(0xFFEF4444),
                    isActive = true, // always solid red
                    onClick = onEndCall,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                controls.forEach { btn -> CallControlButtonView(button = btn) }
            }

            // ── Recent messages strip — SMS + WhatsApp, see RecentMessages.kt ──
            val whatsAppMessages by (
                    NotificationListenerService.whatsAppSource?.bySenderTitle
                        ?: remember { MutableStateFlow<Map<String, RecentMessageItem>>(emptyMap()) }
                    ).collectAsState()
            RecentMessagesStrip(
                phoneNumber = callInfo.phoneNumber,
                displayName = callInfo.displayName,
                whatsAppMessages = whatsAppMessages,
            )
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
 *   a  — [CallPhoneIcon] in red (Lottie, res/raw/call_missed.json).
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

/**
 * Data for a single circular icon button in the ActiveCall State 5 control
 * row. Exactly one of [icon] (Material glyph) or [lottieRawRes] (res/raw
 * Lottie asset, e.g. R.raw.recording) should be set — Record is the only
 * button with a matching Lottie asset today, so it uses [lottieRawRes]
 * while the rest use [icon].
 */
private data class CallControlButton(
    val label: String,
    val icon: ImageVector? = null,
    @androidx.annotation.RawRes val lottieRawRes: Int? = null,
    val activeColor: Color,
    val isActive: Boolean,
    val onClick: () -> Unit,
)

/**
 * Circular icon button used in the ActiveCall State 5 control row (Mute,
 * Speaker, Record, Add call, Keypad, End). Fills solid with
 * [CallControlButton.activeColor] when [CallControlButton.isActive] — icon
 * flips to black on light fill colours so it stays legible — and shows a dim
 * glyph on a neutral dark circle otherwise. `End` is passed with
 * isActive = true so it's always solid red, matching the class kdoc.
 */
@Composable
private fun CallControlButtonView(
    button: CallControlButton,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val bgColor = if (button.isActive) button.activeColor else Color(0xFF2A2A2A)
    val iconColor = if (button.isActive) {
        if (button.activeColor.luminance() > 0.5f) Color.Black else Color.White
    } else Color(0xFFCCCCCC)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(onClick = button.onClick),
        ) {
            if (button.lottieRawRes != null) {
                // isPlaying tied to isActive: sits frozen on its first frame
                // while off, loops (breathing) the moment recording starts —
                // same "only animate when the state is actually live" rule
                // the call equalizer follows.
                MonoLottieIcon(
                    rawRes = button.lottieRawRes,
                    color = iconColor,
                    size = size * 0.5f,
                    isPlaying = button.isActive,
                )
            } else if (button.icon != null) {
                Icon(
                    imageVector = button.icon,
                    contentDescription = button.label,
                    tint = iconColor,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        }
        Text(
            text = button.label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/**
 * Circular caller avatar for the State 5 header. Shows initials (first
 * letter of up to the first two words of [displayName]) on a muted
 * background, or a generic person glyph when the caller couldn't be
 * resolved to a contact ([displayName] is null).
 */
@Composable
private fun CallerAvatar(
    displayName: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val initials = displayName
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotEmpty() }
        ?.take(2)
        ?.joinToString("") { it.first().uppercaseChar().toString() }
        ?.takeIf { it.isNotEmpty() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF3A3A3A)),
    ) {
        if (initials != null) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(size * 0.55f),
            )
        }
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