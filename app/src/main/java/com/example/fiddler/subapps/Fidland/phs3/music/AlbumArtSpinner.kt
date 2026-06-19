package com.example.fiddler.subapps.Fidland.phs3.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.example.fiddler.subapps.Fidland.music.MusicApp
import com.example.fiddler.subapps.Fidland.ui.IslandConfig

/**
 * Phs3 Music — Component 1: circular album-art icon.
 *
 * Rotates continuously while [app].isPlaying is true (vinyl-record style);
 * holds its current rotation angle (does not snap back) when playback stops,
 * and resumes spinning from there if playback restarts.
 *
 * Placed in the pill's LEFT ZONE, immediately left of [NetSpeedDisplay] —
 * see overlay_fidland_pill.kt.
 *
 * @param size Diameter of the circle. Matches [IslandConfig.BASE_SIZE] by
 *             default so it sits flush with the hole-punch spacer's height.
 */
@Composable
fun AlbumArtSpinner(
    app: MusicApp?,
    size: Dp = IslandConfig.BASE_SIZE
) {
    // Continuously increasing angle. While paused, the InfiniteTransition is
    // simply not advanced (animateFloat below is gated on isPlaying), so the
    // rotation Modifier keeps applying the last value — no snap-back.
    val infiniteTransition = rememberInfiniteTransition(label = "album_art_spin")
    val isPlaying = app?.isPlaying == true

    // 8s per full rotation — slow, vinyl-style spin.
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "album_art_angle"
    )

    // Freeze rotation at the last angle when not playing by simply not
    // applying the live `angle` — instead remember the last applied value.
    var heldAngle by remember { mutableStateOf(0f) }
    if (isPlaying) heldAngle = angle

    val displayAngle = if (isPlaying) angle else heldAngle

    val bitmap = app?.albumArt

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF222222))
            .rotate(displayAngle),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            // Simple placeholder dot — avoids depending on the extended
            // material-icons artifact (not included in this project).
            Box(
                modifier = Modifier
                    .size(size * 0.35f)
                    .clip(CircleShape)
                    .background(Color(0xFF555555))
            )
        }
    }
}