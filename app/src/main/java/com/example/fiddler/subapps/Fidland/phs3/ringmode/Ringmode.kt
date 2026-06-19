package com.example.fiddler.subapps.Fidland.phs3.ringmode

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Ring Mode.
 *
 * Shows the current ring mode (Ring / Vibrate / Silent / DND) in the pill
 * and lets the user switch between modes from State 5 without leaving the pill.
 *
 * ── Indicator (State 3) ───────────────────────────────────────────────────────
 * • Mode emoji + short label (e.g. "🔔 Ring", "🌙 Priority only").
 * • Tapping the Indicator cycles Ring → Vibrate → Silent → Ring.
 *   DND is intentionally excluded from the tap cycle — toggling DND
 *   requires MANAGE_NOTIFICATIONS; use the State 5 button instead.
 *
 * ── State 5 ───────────────────────────────────────────────────────────────────
 * Four icon buttons in a row: Ring / Vibrate / Silent / DND.
 * The active mode is highlighted. Tapping any button calls [onModeSelected].
 * When DND is active, a detail line beneath the buttons shows which DND
 * policy is in effect (Priority Only / Alarms Only / Total Silence).
 *
 * @param snapshot       Live ring-mode state.
 * @param onModeSelected Called with the chosen [RingMode] when the user taps
 *                       a button in State 5 or cycles via the Indicator.
 *                       The trigger applies it and reconstructs this handler.
 */
class VolumePhs3Handler(
    private val snapshot: RingmodeSnapshot,
    private val onModeSelected: (RingMode) -> Unit = {},
) : Phs3Handler {

    override val label: String = "Volume"

    // ── Indicator ──────────────────────────────────────────────────────────────

    @Composable
    override fun Indicator() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable {
                // Tap cycles through Ring → Vibrate → Silent → Ring.
                // DND is excluded (requires a separate permission grant).
                val current = snapshot.mode
                val cycleFrom = if (current == RingMode.DND) RingMode.RING else current
                val nextIndex = (RING_TAP_CYCLE.indexOf(cycleFrom) + 1) % RING_TAP_CYCLE.size
                onModeSelected(RING_TAP_CYCLE[nextIndex])
            }
        ) {
            // Mode emoji
            Text(
                text = snapshot.mode.icon,
                fontSize = 13.sp,
            )

            // Label — for DND show the policy sub-type
            val label = if (snapshot.mode == RingMode.DND) {
                snapshot.dndPolicy.displayName
            } else {
                snapshot.mode.displayName
            }
            Text(
                text = label,
                color = indicatorTextColor(snapshot.mode),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }

    // ── State 5 ────────────────────────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            // ── Header ──────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Mode icon in a tinted circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(modeCircleColor(snapshot.mode)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = snapshot.mode.icon, fontSize = 16.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (snapshot.mode == RingMode.DND)
                            "Do Not Disturb"
                        else
                            snapshot.mode.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (snapshot.mode == RingMode.DND)
                            snapshot.dndPolicy.description
                        else
                            ringerVolumeLabel(snapshot),
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                    )
                }
            }

            // ── Divider ──────────────────────────────────────────────────────────
            RingmodeDivider()

            // ── Mode switcher row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RingMode.entries.forEach { mode ->
                    ModeSwitchButton(
                        mode = mode,
                        isActive = snapshot.mode == mode,
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── DND detail ───────────────────────────────────────────────────────
            // Only shown when DND is active — surfaces which policy is in effect.
            if (snapshot.mode == RingMode.DND) {
                RingmodeDivider()

                DndPolicyRow(policy = snapshot.dndPolicy)
            }
        }
    }
}

// ── Mode switcher button ──────────────────────────────────────────────────────

@Composable
private fun ModeSwitchButton(
    mode: RingMode,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isActive) modeCircleColor(mode) else Color(0xFF1A1A1A)
    val labelColor = if (isActive) modeAccentColor(mode) else Color(0xFF666666)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(text = mode.icon, fontSize = 18.sp)
        Text(
            text = mode.displayName,
            color = labelColor,
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── DND policy detail row ─────────────────────────────────────────────────────

@Composable
private fun DndPolicyRow(policy: DndPolicy) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = "🌙", fontSize = 13.sp)
        Column {
            Text(
                text = policy.displayName,
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = policy.description,
                color = Color(0xFF666666),
                fontSize = 9.sp,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Background circle tint behind the mode icon. */
private fun modeCircleColor(mode: RingMode): Color = when (mode) {
    RingMode.RING    -> Color(0xFF1A2A1A)   // dim green
    RingMode.VIBRATE -> Color(0xFF1A1A2A)   // dim blue
    RingMode.SILENT  -> Color(0xFF2A1A1A)   // dim red
    RingMode.DND     -> Color(0xFF1E1A2A)   // dim purple
}

/** Accent / label colour for active mode buttons. */
private fun modeAccentColor(mode: RingMode): Color = when (mode) {
    RingMode.RING    -> Color(0xFF4ADE80)   // green
    RingMode.VIBRATE -> Color(0xFF60A5FA)   // blue
    RingMode.SILENT  -> Color(0xFFFC5C5C)   // red
    RingMode.DND     -> Color(0xFFA78BFA)   // purple
}

/** Text colour for the Indicator label. */
private fun indicatorTextColor(mode: RingMode): Color = when (mode) {
    RingMode.RING    -> Color(0xFF4ADE80)
    RingMode.VIBRATE -> Color(0xFF60A5FA)
    RingMode.SILENT  -> Color(0xFFFC5C5C)
    RingMode.DND     -> Color(0xFFA78BFA)
}

/** Human-readable ringer volume, e.g. "Volume · 5 / 7". */
private fun ringerVolumeLabel(snapshot: RingmodeSnapshot): String =
    if (snapshot.ringerMaxVolume > 0)
        "Volume · ${snapshot.ringerVolume} / ${snapshot.ringerMaxVolume}"
    else
        ""

/** Thin horizontal divider, consistent with other phs3 State 5 panels. */
@Composable
private fun RingmodeDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}