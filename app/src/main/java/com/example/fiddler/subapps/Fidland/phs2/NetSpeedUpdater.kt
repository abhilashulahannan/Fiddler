package com.example.fiddler.subapps.Fidland.phs2

import android.net.TrafficStats
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Phase 2 — left arm of the pill.
 * Reads real TrafficStats every second and renders upload (top) / download (bottom).
 * Layout mirrors NTSPD: two rows stacked vertically, dominant direction bold + larger.
 */
@Composable
fun NetSpeedDisplay() {
    var lastRx by remember { mutableStateOf(TrafficStats.getTotalRxBytes()) }
    var lastTx by remember { mutableStateOf(TrafficStats.getTotalTxBytes()) }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var rxSpeed by remember { mutableStateOf(0L) }
    var txSpeed by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val nowRx   = TrafficStats.getTotalRxBytes()
            val nowTx   = TrafficStats.getTotalTxBytes()
            val nowTime = System.currentTimeMillis()

            val elapsed = (nowTime - lastTime).coerceAtLeast(1L)
            rxSpeed = ((nowRx - lastRx) * 1000L / elapsed).coerceAtLeast(0L)
            txSpeed = ((nowTx - lastTx) * 1000L / elapsed).coerceAtLeast(0L)

            lastRx   = nowRx
            lastTx   = nowTx
            lastTime = nowTime
        }
    }

    val txDominant = txSpeed > rxSpeed
    val textColor  = LocalContentColor.current  // inherits white from the black pill

    Column(
        modifier             = Modifier.wrapContentSize(),
        horizontalAlignment  = Alignment.Start,
        verticalArrangement  = Arrangement.Center
    ) {
        // Upload — top row
        Text(
            text       = formatSpeed(txSpeed, "↑"),
            color      = textColor,
            fontSize   = if (txDominant) 10.sp else 8.sp,
            fontWeight = if (txDominant) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1
        )
        // Download — bottom row
        Text(
            text       = formatSpeed(rxSpeed, "↓"),
            color      = textColor,
            fontSize   = if (!txDominant) 10.sp else 8.sp,
            fontWeight = if (!txDominant) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1
        )
    }
}

/**
 * Formats bytes/s as an AnnotatedString: arrow + value + half-size unit label.
 * Mirrors NTSPD's RelativeSizeSpan(0.5f) behaviour.
 */
private fun formatSpeed(speedBytes: Long, arrow: String): AnnotatedString {
    var value = speedBytes / 1024.0
    var unit  = "KB/s"
    if (value >= 1024) { value /= 1024; unit = "MB/s" }
    if (value >= 1024) { value /= 1024; unit = "GB/s" }
    val display = if (value < 10) "%.1f".format(value) else "%.0f".format(value)

    return buildAnnotatedString {
        append("$arrow$display ")
        withStyle(SpanStyle(fontSize = 0.5.em)) {
            append(unit)
        }
    }
}