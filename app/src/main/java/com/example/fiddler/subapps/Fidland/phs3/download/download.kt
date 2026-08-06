package com.example.fiddler.subapps.Fidland.phs3.download

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.BlockAffinity
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler

/**
 * Phs3 module — Active download.
 *
 * ── §B7 Blocks (Phase 4 — from-scratch build) ─────────────────────────────────
 * Resolves the design doc's three open Blocks questions:
 *
 *   • **Location a — generic download glyph, not network-type.** The
 *     pre-build version's [DownloadNetworkIcon] branched on
 *     [DownloadNetworkType], but every source has always hardcoded
 *     [DownloadNetworkType.UNKNOWN] — the icon could only ever show its dim
 *     fallback dot. Per the doc's own framing ("clean resolution, not a
 *     fix-first blocker"), that path is retired outright rather than wired
 *     up to real connectivity data: [LocationAContent] now shows
 *     [GenericDownloadIcon], a plain download-arrow glyph, unconditionally.
 *   • **Indicator (primary block, now [BlockAffinity.DYNAMIC])** — the
 *     progress ring + percentage, unchanged content from the pre-build
 *     version, just redeclared dynamic instead of implicitly fixed-right.
 *   • **SecondaryIndicator (secondary block, [BlockAffinity.RIGHT_ANCHOR])**
 *     — filename text, new this pass. Resolves the doc's open "side not
 *     specified" flag: adopted as right-fixed via [hasSecondaryBlock], not
 *     dynamic and not paired inside the % block — flagged as a default
 *     choice, not a confirmed spec answer, same treatment other Phase 4
 *     entities gave their own unspecified placement questions.
 *
 * ETA and speed are deliberately NOT compact-indicator blocks — the doc's
 * State5 spec lists them as State5-only detail (see [State5Content]), so
 * the compact view stays just ring/%/filename; nothing here regresses that.
 *
 * ── State 5 (long-press) — from-scratch build, not a migration ────────────────
 * The pre-build version was an explicit unimplemented placeholder. Built now:
 *   • File title (already available — [DownloadInfo.title]).
 *   • Which-app — [DownloadInfo.resolveAppLabel], "Unknown app" fallback
 *     when unresolvable (only [NotificationDownloadSource] has an
 *     attribution path — see [DownloadInfo.packageName]'s doc).
 *   • Total size — [formatBytes], "—" when [DownloadInfo.totalBytes] is null
 *     (indeterminate/Traffic-only entries).
 *   • ETA — [DownloadInfo.derivedEtaMs], real derivation from bytes
 *     remaining / current speed; "—" when not derivable (Traffic-only).
 *   • Current speed — [formatSpeed], already worked pre-build.
 *   • A larger progress ring, reusing [DownloadProgressRing].
 * No pause/resume/cancel controls — no source in this build has a control
 * API into the underlying download (same "observer, not controller" shape
 * as Timer/Stopwatch and Alarm's Snooze/Cancel caveat), so State5 is
 * read-only detail rather than a placeholder claiming controls that don't
 * exist.
 *
 * @param downloadInfo Live snapshot of the active download. DownloadPhs3Trigger
 *                      reconstructs this handler on every aggregator emission.
 */
class DownloadPhs3Handler(
    private val downloadInfo: DownloadInfo
) : Phs3Handler {

    override val label: String = "Download"

    // ── Location a (LEFT ZONE) — generic download glyph (Phase 4) ────────────

    override val hasLocationA: Boolean = true

    @Composable
    override fun LocationAContent() {
        GenericDownloadIcon(size = 16.dp)
    }

    // ── Indicator — primary block, right zone (Phase 4: declared DYNAMIC) ────

    override val blockAffinity: BlockAffinity = BlockAffinity.DYNAMIC

    @Composable
    override fun Indicator() {
        DownloadProgressRing(
            progressFraction = downloadInfo.progressFraction,
            size             = 22.dp
        )
    }

    // ── SecondaryIndicator — secondary block, right zone (Phase 4 — new) ─────
    // Filename text — adopted right-fixed placement, see class doc.

    override val hasSecondaryBlock: Boolean = true
    override val secondaryBlockAffinity: BlockAffinity = BlockAffinity.RIGHT_ANCHOR

    @Composable
    override fun SecondaryIndicator() {
        Text(
            text       = downloadInfo.title,
            color      = Color.White,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .width(72.dp)
                .padding(start = 6.dp),
        )
    }

    // ── State 5 — from-scratch build, see class doc ───────────────────────────

    @Composable
    override fun State5Content() {
        val context = LocalContext.current
        val appLabel = downloadInfo.resolveAppLabel(context) ?: "Unknown app"
        val totalText = downloadInfo.totalBytes?.let { formatBytes(it) } ?: "—"
        val etaText = downloadInfo.derivedEtaMs()?.let { formatEta(it) } ?: "—"
        val speedText = formatSpeed(downloadInfo.speedBps) ?: "—"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Header: big ring + title/app ───────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DownloadProgressRing(
                    progressFraction = downloadInfo.progressFraction,
                    size             = 48.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = downloadInfo.title,
                        color      = Color.White,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = appLabel,
                        color    = Color(0xFF888888),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            S5Divider()

            // ── Stat rows: size / ETA / speed ──────────────────────────────
            StatRow(label = "Size", value = totalText)
            StatRow(label = "Time remaining", value = etaText)
            StatRow(label = "Speed", value = speedText)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text     = label,
            color    = Color(0xFF888888),
            fontSize = 12.sp,
        )
        Text(
            text       = value,
            color      = Color.White,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Thin horizontal rule — matches the style used in AlarmPhs3Handler/FootballPhs3Handler. */
@Composable
private fun S5Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}

// ── Location-a: generic download glyph (Phase 4 — replaces network-type icon) ─

/**
 * A plain download-arrow-into-tray glyph — deliberately generic, since no
 * source has ever populated real [DownloadNetworkType] data (see class
 * doc's "Location a" note). Drawn on a 20×20 virtual grid, scaled to [size],
 * matching [DownloadNetworkIcon]'s old canvas-based sizing convention so
 * this drops into the same location-a slot with no layout change.
 */
@Composable
fun GenericDownloadIcon(size: Dp = 16.dp) {
    val color = Color.White
    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            val scale = size.toPx() / 20f
            fun pt(x: Float, y: Float) = Offset(x * scale, y * scale)
            val sw = 1.6f * scale

            // Downward arrow shaft + head
            drawLine(
                color = color,
                start = pt(10f, 3f),
                end   = pt(10f, 12f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = pt(6f, 8f),
                end   = pt(10f, 12f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = pt(14f, 8f),
                end   = pt(10f, 12f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            // Tray
            drawLine(
                color = color,
                start = pt(4f, 16f),
                end   = pt(16f, 16f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
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

        // Percentage label via native canvas — precise control at small sizes
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