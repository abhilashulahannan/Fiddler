package com.example.fiddler.ui.icons

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Plays a looping (by default) Lottie animation, recoloured to a solid
 * [color] — every asset ships pure black, and the app tints them at render
 * time rather than shipping a coloured variant per state.
 *
 * ── How the recolour works ───────────────────────────────────────────────
 * `LottieAnimation` has no built-in colour-filter parameter, so this stencils
 * [color] on top of the composition's own drawn pixels: it composites the
 * animation into its own offscreen layer, then draws a solid rect over it
 * with `BlendMode.SrcAtop` — SrcAtop only paints where the layer already has
 * non-transparent pixels, so transparent areas stay transparent and the
 * animation's shape becomes a stencil for [color]. The offscreen compositing
 * step is required so this only recolours the icon's own pixels, not
 * whatever happens to be drawn underneath it. (This is the same technique
 * AlarmClockIcon used before it was centralised here.)
 *
 * ── Two ways to call this ────────────────────────────────────────────────
 * Most call sites just want `MonoLottieIcon(rawRes = R.raw.foo, size = ...)`
 * — colour comes from [LottieIconColors] automatically. Pass `color =`
 * explicitly to override for state-driven cases (alarm urgency, missed vs.
 * active call, etc). For the rare non-res/raw asset (e.g. an asset-folder
 * Lottie file), use the [spec] overload instead, which requires an explicit
 * [color] since there's no raw-res id to look one up by.
 *
 * @param modifier   Applied before sizing/recolouring. Add layout modifiers
 *                   (padding, mirroring, etc.) here rather than via [size].
 * @param size       Convenience square size; omit and size via [modifier]
 *                   instead if you need non-square or already-sized content.
 * @param iterations Loop count; defaults to looping forever.
 * @param isPlaying  Set false to freeze on the current frame.
 * @param speed      Playback speed multiplier (e.g. record's paused 0.3×).
 */
@Composable
fun MonoLottieIcon(
    spec: LottieCompositionSpec,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    iterations: Int = LottieConstants.IterateForever,
    isPlaying: Boolean = true,
    speed: Float = 1f,
) {
    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations  = iterations,
        isPlaying   = isPlaying,
        speed       = speed,
    )

    val sized = if (size != null) modifier.size(size) else modifier

    LottieAnimation(
        composition  = composition,
        progress     = { progress },
        // Not every asset is a square 1:1 composition — some are cropped to a
        // non-square aspect ratio. ContentScale.FillBounds stretches the
        // composition to exactly match the (square) slot passed via
        // `size`/`modifier`, which distorts any non-square asset. Fit scales
        // uniformly instead, so square assets still fill their slot exactly
        // (no regression there) while non-square ones keep their own aspect
        // ratio and letterbox within the slot rather than being stretched.
        contentScale = ContentScale.Fit,
        modifier     = sized
            // clipToBounds() first: without it, the offscreen layer below can
            // be recorded a hair larger than the laid-out square — on-device
            // this shows up as a single stray pixel of `color` in the icon's
            // top-left corner (every icon, since it comes from this shared
            // composable, not from any one asset). Clipping to the exact
            // layout bounds before compositing removes that leaked texel.
            .clipToBounds()
            // Required so the SrcAtop blend below only affects this layer's
            // own drawn pixels, not whatever sits underneath it in the pill.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(color = color, blendMode = BlendMode.SrcAtop)
            },
    )
}

/**
 * Convenience overload for the common case: a res/raw Lottie asset whose
 * colour should come from [LottieIconColors]. See the primary overload's
 * kdoc for how the recolour itself works.
 */
@Composable
fun MonoLottieIcon(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    color: Color = LottieIconColors.forRawRes(rawRes),
    iterations: Int = LottieConstants.IterateForever,
    isPlaying: Boolean = true,
    speed: Float = 1f,
) = MonoLottieIcon(
    spec        = LottieCompositionSpec.RawRes(rawRes),
    color       = color,
    modifier    = modifier,
    size        = size,
    iterations  = iterations,
    isPlaying   = isPlaying,
    speed       = speed,
)

/**
 * Static-vector counterpart to [MonoLottieIcon], for tiles that don't have a
 * dedicated Lottie asset yet (currently: Dev Options, Accessibility — see
 * QuickSettingsTopic.kt). Uses a Material Symbols [ImageVector] instead of a
 * res/raw animation.
 *
 * No SrcAtop stencil needed here — unlike Lottie compositions, `Icon` already
 * tints its vector directly via `tint`, so this is a thin wrapper that just
 * matches [MonoLottieIcon]'s call signature (`color`, `size`) so call sites
 * can treat the two interchangeably.
 *
 * Swap a tile from this to [MonoLottieIcon] once its Lottie asset ships —
 * no other call-site changes needed beyond the icon reference itself.
 */
@Composable
fun MonoVectorIcon(
    imageVector: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp? = null,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    Icon(
        imageVector        = imageVector,
        contentDescription = null,
        tint               = color,
        modifier           = sized,
    )
}