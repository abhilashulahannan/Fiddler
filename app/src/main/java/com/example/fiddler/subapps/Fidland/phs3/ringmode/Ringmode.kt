package com.example.fiddler.subapps.Fidland.phs3.ringmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.LottieIconColors
import com.example.fiddler.ui.icons.MonoLottieIcon

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

    // Header row + divider + switcher row (now with 30.dp icons) need a bit
    // more vertical room than the shared IslandConfig.STATE5_HEIGHT default,
    // and the DND detail row needs even more when it's showing.
    override val state5HeightOverride: Dp =
        if (snapshot.mode == RingMode.DND) 160.dp else 128.dp

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
            // Mode icon
            RingModeIcon(mode = snapshot.mode, size = 13.dp)

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
                // Mode icon — no background plate. Monochrome glyph tinted with
                // the mode's accent colour so it reads clearly against the
                // pill's black backdrop on its own.
                RingModeIcon(
                    mode = snapshot.mode,
                    size = 26.dp,
                    color = modeAccentColor(snapshot.mode),
                )

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

                DndPolicyRow(policy = snapshot.dndPolicy)
            }
        }
    }
}

// ── Mode icon ──────────────────────────────────────────────────────────────────

/** Shared mode icon — plays the matching ring_*.json Lottie asset on loop. */
@Composable
private fun RingModeIcon(mode: RingMode, size: Dp, color: Color? = null) {
    // Colour comes from LottieIconColors.ringRing/ringVibrate/ringSilent/
    // ringDnd via the rawRes lookup by default — matches modeAccentColor
    // below by design. Pass [color] to override (e.g. dim it for an
    // inactive switcher button).
    if (color != null) {
        MonoLottieIcon(rawRes = mode.rawRes, size = size, color = color)
    } else {
        MonoLottieIcon(rawRes = mode.rawRes, size = size)
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
    // No background plate — the monochrome icon itself is the tappable
    // target. Active/inactive reads purely through tint: full accent colour
    // when selected, dimmed grey otherwise. Sits directly on the pill's
    // black backdrop, which is exactly where these glyphs contrast best.
    val tint = if (isActive) modeAccentColor(mode) else Color(0xFF555555)
    val labelColor = if (isActive) modeAccentColor(mode) else Color(0xFF666666)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        RingModeIcon(mode = mode, size = 30.dp, color = tint)
        Text(
            text = mode.displayName,
            color = labelColor,
            fontSize = 10.sp,
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
        RingModeIcon(mode = RingMode.DND, size = 15.dp)
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

/**
 * Accent / label colour for active mode buttons — same values as each
 * mode's icon tint, so both move together if you tweak
 * [LottieIconColors].
 */
private fun modeAccentColor(mode: RingMode): Color = when (mode) {
    RingMode.RING    -> LottieIconColors.ringRing
    RingMode.VIBRATE -> LottieIconColors.ringVibrate
    RingMode.SILENT  -> LottieIconColors.ringSilent
    RingMode.DND     -> LottieIconColors.ringDnd
}

/** Text colour for the Indicator label — mirrors [modeAccentColor]. */
private fun indicatorTextColor(mode: RingMode): Color = modeAccentColor(mode)

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