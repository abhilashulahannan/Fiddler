package com.example.fiddler.subapps.Fidland.phs3.ride

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Ride hailing (Uber, Ola, Rapido).
 *
 * ── State 3 indicator layout ─────────────────────────────────────────────────
 * PRE_RIDE:  [app icon]  ·  OTP 4821  ·  3 min
 * IN_RIDE:   [app icon]  ·  12 min  ·  [progress arc — only if dest known]
 *
 * The app icon sits in location a (left of hole-punch, same slot as AlbumArtSpinner).
 * The rest of the content goes in the right zone via [Indicator].
 *
 * ── State 5 panel ────────────────────────────────────────────────────────────
 * PRE_RIDE: driver name, vehicle, rating, large OTP, driver ETA
 * IN_RIDE:  driver name, vehicle, rating, large ETA countdown, progress circle,
 *           "Open app" tap-through
 *
 * ── Wire-up ──────────────────────────────────────────────────────────────────
 * In the pill's left-zone composable (same pattern as AlbumArtSpinner):
 *
 *   if (activePhs3Handler is RidePhs3Handler) {
 *       (activePhs3Handler as RidePhs3Handler).LocationAIndicator()
 *   }
 */
class RidePhs3Handler : Phs3Handler {

    override val label: String = "Ride"

    // ── Location a — app icon (left of hole-punch) ────────────────────────────

    @Composable
    fun LocationAIndicator() {
        val snap by RideRepository.flow.collectAsState()
        if (!snap.isActive || snap.app == null) return

        RideAppIcon(app = snap.app!!, size = 18.dp)
    }

    // ── State 3 indicator — right zone content ────────────────────────────────

    @Composable
    override fun Indicator() {
        val snap by RideRepository.flow.collectAsState()
        if (!snap.isActive) return

        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            when (snap.phase) {
                RidePhase.PRE_RIDE -> PreRideIndicatorContent(snap)
                RidePhase.IN_RIDE  -> InRideIndicatorContent(snap)
                RidePhase.ENDED    -> EndedIndicatorContent()
                RidePhase.IDLE     -> Unit
            }
        }
    }

    // ── State 5 panel ─────────────────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        val snap by RideRepository.flow.collectAsState()
        val context = LocalContext.current

        if (!snap.isActive) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active ride", color = Color(0xFF666666), fontSize = 12.sp)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            // ── Driver info row ───────────────────────────────────────────────
            if (snap.driverName.isNotBlank() || snap.vehicleInfo.isNotBlank()) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        if (snap.driverName.isNotBlank()) {
                            Text(
                                text       = snap.driverName,
                                color      = Color.White,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (snap.vehicleInfo.isNotBlank()) {
                            Text(
                                text     = snap.vehicleInfo,
                                color    = Color(0xFF888888),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (snap.driverRating.isNotBlank()) {
                        Text(
                            text       = snap.driverRating,
                            color      = Color(0xFFFDD835),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color(0xFF2A2A2A))
            )

            // ── Central info — OTP (pre) or ETA + progress (in-ride) ─────────
            when (snap.phase) {
                RidePhase.PRE_RIDE -> PreRidePanel(snap)
                RidePhase.IN_RIDE, RidePhase.ENDED -> InRidePanel(snap)
                else -> Unit
            }

            // ── Open app button ───────────────────────────────────────────────
            snap.app?.let { app ->
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A1A))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.launchAction))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "Open ${app.displayName}",
                        color      = Color(0xFF4FC3F7),
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  State 3 sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreRideIndicatorContent(snap: RideSnapshot) {
    // OTP
    if (snap.otp.isNotBlank()) {
        Text(
            text       = "OTP ${snap.otp}",
            color      = Color(0xFFFDD835),   // amber — stands out
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
        )
        DotSeparator()
    }
    // Driver ETA
    if (snap.driverEtaText.isNotBlank()) {
        Text(
            text     = snap.driverEtaText,
            color    = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun InRideIndicatorContent(snap: RideSnapshot) {
    // ETA to destination
    if (snap.destEtaText.isNotBlank()) {
        Text(
            text       = snap.destEtaText,
            color      = Color.White,
            fontSize   = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
        )
    }
    // Progress arc — only when destination is known
    if (snap.hasProgress) {
        DotSeparator()
        RideProgressArc(
            fraction        = snap.progressFraction,
            size            = 14.dp,
            trackColor      = Color(0xFF333333),
            progressColor   = Color(0xFF4FC3F7),
            strokeWidthDp   = 2f,
        )
    }
}

@Composable
private fun EndedIndicatorContent() {
    Text(
        text     = "Ride ended",
        color    = Color(0xFF888888),
        fontSize = 10.sp,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  State 5 sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreRidePanel(snap: RideSnapshot) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text     = "Show driver this OTP",
            color    = Color(0xFF888888),
            fontSize = 9.sp,
        )
        // Large OTP
        Text(
            text       = snap.otp.ifBlank { "---" },
            color      = Color(0xFFFDD835),
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
        )
        if (snap.driverEtaText.isNotBlank()) {
            Text(
                text     = "Arriving in ${snap.driverEtaText}",
                color    = Color(0xFFAAAAAA),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun InRidePanel(snap: RideSnapshot) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // ETA text block
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = snap.destEtaText.ifBlank { "--" },
                color      = Color.White,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = "to destination",
                color    = Color(0xFF888888),
                fontSize = 9.sp,
            )
            if (snap.destinationName.isNotBlank()) {
                Text(
                    text     = snap.destinationName,
                    color    = Color(0xFF555555),
                    fontSize = 8.sp,
                    maxLines = 1,
                )
            }
        }

        // Large progress circle — only if destination known
        if (snap.hasProgress) {
            RideProgressArc(
                fraction      = snap.progressFraction,
                size          = 52.dp,
                trackColor    = Color(0xFF2A2A2A),
                progressColor = Color(0xFF4FC3F7),
                strokeWidthDp = 5f,
                showPercent   = true,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Deterministic arc when [fraction] ≥ 0, indeterminate slow spin otherwise.
 * Indeterminate spin is shown when IN_RIDE but destination hasn't resolved yet —
 * this branch is currently unreachable because [RideSnapshot.hasProgress]
 * gates on fraction ≥ 0, but the composable handles it defensively.
 */
@Composable
fun RideProgressArc(
    fraction: Float,
    size: Dp,
    trackColor: Color,
    progressColor: Color,
    strokeWidthDp: Float = 2.5f,
    showPercent: Boolean = false,
) {
    val isDeterminate = fraction >= 0f

    // Animate progress smoothly when determinate
    val animatedFraction by animateFloatAsState(
        targetValue    = if (isDeterminate) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec  = tween(durationMillis = 600),
        label          = "ride_progress",
    )

    // Indeterminate spinner angle
    val infiniteTransition = rememberInfiniteTransition(label = "ride_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ride_spin_angle",
    )

    Box(
        modifier          = Modifier.size(size),
        contentAlignment  = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round)
            val inset  = strokeWidthDp.dp.toPx() / 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color      = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = stroke,
            )

            // Progress / spinner
            if (isDeterminate) {
                drawArc(
                    color      = progressColor,
                    startAngle = -90f,
                    sweepAngle = animatedFraction * 360f,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
            } else {
                drawArc(
                    color      = progressColor,
                    startAngle = spinAngle - 90f,
                    sweepAngle = 90f,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = stroke,
                )
            }
        }

        // Percentage label inside large arc (State 5 only)
        if (showPercent && isDeterminate) {
            Text(
                text     = "${(animatedFraction * 100).toInt()}%",
                color    = Color(0xFFAAAAAA),
                fontSize = (size.value * 0.22f).sp,
            )
        }
    }
}

/** Ride app coloured dot or letter badge in location a. */
@Composable
fun RideAppIcon(app: RideApp, size: Dp) {
    val bgColor = when (app) {
        RideApp.UBER   -> Color.Black
        RideApp.OLA    -> Color(0xFF1DB954)
        RideApp.RAPIDO -> Color(0xFFFDD835)
    }
    val letter = app.displayName.first().toString()

    Box(
        modifier         = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = letter,
            color      = if (app == RideApp.RAPIDO) Color.Black else Color.White,
            fontSize   = (size.value * 0.55f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DotSeparator() {
    Text(text = "·", color = Color(0xFF444444), fontSize = 10.sp)
}