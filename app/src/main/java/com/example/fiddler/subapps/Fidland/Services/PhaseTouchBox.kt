package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhaseTouchBox(
    overlayX: Float = 0f,
    overlayY: Float = 0f,
    overlayHeight: Float = 0f,
    widthDp: Float = 200f,
    heightDp: Float = 75f,
    gestureListener: suspend () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var flashColor by remember { mutableStateOf(Color.Transparent) }

    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }

    Box(
        modifier = Modifier
            .offset(x = overlayX.dp, y = (overlayY + overlayHeight).dp)
            .size(widthDp.dp, heightDp.dp)
            .background(flashColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { scope.launch { gestureListener() } }
                )
            }
    )

    // Flash effect
    suspend fun flash(duration: Long = 1000) {
        flashColor = Color(0x80000000)
        delay(duration)
        flashColor = Color.Transparent
    }

    // Example usage: call flash from parent
    // scope.launch { flash() }
}
