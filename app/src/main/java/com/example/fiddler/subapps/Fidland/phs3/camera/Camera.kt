package com.example.fiddler.subapps.Fidland.phs3.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.BlockSide
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import com.example.fiddler.ui.icons.MonoLottieIcon

/**
 * Phs3 module — Camera sensor indicator.
 *
 * Qualifies while at least one camera sensor is open, system-wide — see
 * [CameraPhs3Trigger] for the CameraManager.AvailabilityCallback wiring
 * (mirrors Android's own camera-in-use privacy indicator). See
 * [CameraPhs3Trigger]'s class doc for the §B6 Special-Condition wiring
 * (sub-score 85, held indefinitely) driven off the same signal.
 *
 * ── Indicator (State 3) ─────────────────────────────────────────────────────
 * Just the looping camera Lottie icon (res/raw/camera.json) — kept
 * deliberately compact, same footprint as FlashlightPhs3Handler.Indicator.
 *
 * ── State 5 ───────────────────────────────────────────────────────────────
 * None — a privacy indicator only needs to be visible while active; there's
 * no detail worth expanding into a panel. [hasState5Content] returns false
 * so swipe-down goes straight to DASHBOARD, same as any other module with
 * nothing to show there.
 *
 * ── Placement (§B2) ──────────────────────────────────────────────────────────
 * Design doc §B7 calls for a conditional, rule-based side (right when
 * Camera is the only entity shown, left when co-displayed alongside
 * another) — not expressible with today's [BlockAffinity] (it's not
 * width-driven, so [BlockAffinity.DYNAMIC] doesn't fit) — the design doc
 * itself flags this as possibly needing its own classification tier
 * (§B8 #12), still unbuilt.
 *
 * ⚠ Deliberate deviation, not a resolution: set to [BlockAffinity.DYNAMIC]
 * anyway, at explicit request, ahead of that tier existing. This means
 * Camera's side is decided by §B2's width-balancer (whichever side keeps
 * the pill narrower) rather than by the co-display rule the spec actually
 * wants — Camera isn't co-displayed with anything today (rotation shows one
 * real handler at a time), so in practice this only ever matters once
 * co-display itself is rendered, at which point width-driven placement is
 * very likely the *wrong* rule for it (see [Phs3Handler.coDisplay]'s doc — a
 * co-displayed icon's correct side is "not wherever the currently-shown
 * entity is," not "wherever the balancer's width math prefers"). Left here
 * as-is per instruction; revisit before or alongside building co-display —
 * do not treat this as the tier decision from §B8 #12 being made.
 *
 * ── Co-display ──────────────────────────────────────────────────────────────
 * [coDisplay] = true, declaring Camera's additive intent (render alongside
 * whichever handler currently holds the slot, never displace it) as real,
 * typed data on the handler — see [Phs3Handler.coDisplay]'s doc.
 *
 * §B8 #12 resolved: rather than a new [BlockAffinity] tier, Camera keeps
 * `blockAffinity = DYNAMIC` and instead overrides [coDisplaySide] to
 * `BlockSide.LEFT` — Camera computes its own side and hands it to the
 * balancer, which forces it (see [Phs3BlockBalancer]/[Phs3Block.forcedSide])
 * instead of resolving by width. This only applies while actually
 * co-displaying; when Camera is the sole/primary handler, ordinary
 * width-based `DYNAMIC` resolution still applies (see [coDisplaySide]'s own
 * doc) — so "right if alone" needs no separate code path here.
 *
 * §B8 #13 resolved: an exclusive indefinite-hold entity (Call) suppresses
 * co-display entirely while active — Camera's icon does not show alongside
 * Call's. That suppression is a rendering-layer decision (whether Camera
 * even reaches the balancer as a co-display candidate this frame), not
 * something Camera's own handler needs to know about.
 *
 * ⚠ Still unbuilt: the rendering wiring that actually reads [coDisplay] /
 * [coDisplaySide] and places co-display blocks in the pill — that's
 * `overlay_fidland_pill.kt`'s `PillLeftZoneContent`/`RightIndicatorContent`,
 * a separate, larger change (independent per-block measurement for an
 * arbitrary list of co-displaying handlers, not just one active handler's
 * primary/secondary blocks). This handler and the balancer are ready for
 * it; the zone-rendering side is the remaining step.
 */
class CameraPhs3Handler : Phs3Handler {

    override val label: String = "Camera"

    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC

    override val coDisplay: Boolean = true

    override val coDisplaySide: BlockSide = BlockSide.LEFT

    override fun hasState5Content(): Boolean = false

    @Composable
    override fun Indicator() {
        Box(
            modifier         = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            MonoLottieIcon(rawRes = R.raw.camera, size = 26.dp)
        }
    }
}