package com.example.fiddler.subapps.Fidland.phs3.download

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Active download.
 *
 * ── Zone layout (BOTH_EXPANDED) ──────────────────────────────────────────────
 *
 *   [ LEFT ZONE                    ] [ ● cam ] [ RIGHT ZONE   ]
 *   [ 📶  ↓2.3 MB/s                ] [        ] [ 14m    ◯45% ]
 *     L1    phs2 net speed                       L2 ETA  L3 ring
 *
 * L1 — [LocationAIndicator]: WiFi fan icon or "3G"/"4G"/"5G" label showing
 *       the network type used for the download. Placed LEFT of NetSpeedDisplay
 *       in location a. Called from overlay_fidland_pill.kt's left-zone slot.
 *
 * L2 — ETA text: "14m", "42s", "—" if unknown. Location b (immediate right
 *       of hole punch).
 *
 * L3 — [DownloadProgressRing]: thin circular arc with % inside. Location c
 *       (right of ETA). Turns green at 100%.
 *
 * [Indicator] renders ONLY L2 + L3 (the right zone). L1 is deliberately
 * separated into [LocationAIndicator] so the right arm stays slim and the
 * pill is balanced around the camera hole.
 *
 * ── State 5 (long-press) ─────────────────────────────────────────────────────
 * Placeholder for pause/resume/cancel/open-file actions. Not yet implemented.
 *
 * @param downloadInfo Live snapshot of the active download. DownloadPhs3Trigger
 *                      reconstructs this handler on every aggregator emission.
 */
class DownloadPhs3Handler(
    private val downloadInfo: DownloadInfo
) : Phs3Handler {

    override val label: String = "Download"

    // ── Location a (LEFT ZONE) — network type icon ────────────────────────────
    // Contributes a slot to the location-a row when this handler is qualified.
    // Renders a WiFi fan glyph or a "3G"/"4G"/"5G" text badge so the user
    // knows which connection is carrying the download.

    override val hasLocationA: Boolean = true

    @Composable
    override fun LocationAContent() {
        DownloadNetworkIcon(
            networkType = downloadInfo.networkType,
            size        = 16.dp
        )
    }

    // ── Indicator — RIGHT ZONE (L2: ETA, L3: progress ring) ──────────────────
    // Only these two items go on the right so the pill stays compact.
    // SpaceBetween / spacedBy keeps them tightly packed.

    @Composable
    override fun Indicator() {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // L2 — ETA (+ optional speed on a second line)
            EtaTextColumn(downloadInfo = downloadInfo)

            // L3 — circular progress ring with % inside
            DownloadProgressRing(
                progressFraction = downloadInfo.progressFraction,
                size             = 22.dp
            )
        }
    }

    // ── State 5 ControlsPanel ────────────────────────────────────────────────

    @Composable
    override fun State5Content() {
        // TODO: pause/resume toggle, cancel, open-file-on-completion actions.
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = "Download controls — coming soon",
                color    = Color(0xFF666666),
                fontSize = 13.sp
            )
        }
    }
}

// ── L2: ETA text column ───────────────────────────────────────────────────────

/**
 * Two-line text column for location b:
 *   Line 1 (white, medium)  — ETA,   e.g. "14m" / "42s" / "—"
 *   Line 2 (dim, optional)  — speed, e.g. "2.3 MB/s" (omitted if unknown)
 *
 * A fixed [width] is NOT applied here; the right-zone Box is measured via
 * onSizeChanged in overlay_fidland_pill.kt, so the pill auto-sizes to fit
 * whatever this column reports. If the ETA text length fluctuates (e.g. "2h 5m"
 * vs "42s") you may add a fixed width here to prevent pill jitter — match the
 * widest expected string at 11.sp.
 */
@Composable
private fun EtaTextColumn(downloadInfo: DownloadInfo) {
    val eta   = formatEta(downloadInfo.etaMs)
    val speed = formatSpeed(downloadInfo.speedBps)

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text       = eta,
            color      = Color.White,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines   = 1
        )
        if (speed != null) {
            Text(
                text     = speed,
                color    = Color(0xFF888888),
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}

// ── L3: Circular progress ring ────────────────────────────────────────────────

/**
 * Thin circular progress arc with integer percentage drawn in the centre.
 *
 * Track    : dim grey  (#2A2A2A)
 * Progress : white → green (#22C55E) at 100 %
 * Label    : white, centred, native canvas (precise font control at small sizes)
 *
 * @param progressFraction Value in [0f, 1f]. Clamped internally.
 * @param size             Ring diameter; default 22.dp.
 */
@Composable
fun DownloadProgressRing(
    progressFraction: Float,
    size: Dp = 22.dp
) {
    val clamped    = progressFraction.coerceIn(0f, 1f)
    val percent    = (clamped * 100).toInt()
    val arcColor   = if (clamped >= 1f) Color(0xFF22C55E) else Color.White
    val trackColor = Color(0xFF2A2A2A)

    Box(
        modifier         = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 2.2f * (size.toPx() / 22f)
            val inset  = strokeWidth / 2f
            val oval   = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color       = trackColor,
                startAngle  = -90f,
                sweepAngle  = 360f,
                useCenter   = false,
                topLeft     = topLeft,
                size        = oval,
                style       = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            if (clamped > 0f) {
                drawArc(
                    color      = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = oval,
                    style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Percentage label via native canvas — precise control at 22dp
        Canvas(modifier = Modifier.size(size)) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color     = android.graphics.Color.WHITE
                    textSize  = this@Canvas.size.width * 0.28f
                    typeface  = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT_BOLD,
                        android.graphics.Typeface.BOLD
                    )
                    textAlign  = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(
                    "$percent",
                    this@Canvas.size.width  / 2f,
                    this@Canvas.size.height / 2f + paint.textSize / 3f,
                    paint
                )
            }
        }
    }
}