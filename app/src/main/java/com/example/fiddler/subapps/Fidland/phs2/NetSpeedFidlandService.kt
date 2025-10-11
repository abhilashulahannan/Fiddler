package com.example.fiddler.subapps.Fidland.phs2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.net.TrafficStats

@Composable
fun NetworkTrafficDisplay() {
    var lastRx by remember { mutableStateOf(TrafficStats.getTotalRxBytes()) }
    var lastTx by remember { mutableStateOf(TrafficStats.getTotalTxBytes()) }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var rxSpeed by remember { mutableStateOf(0L) }
    var txSpeed by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val nowTime = System.currentTimeMillis()

            rxSpeed = ((nowRx - lastRx) * 1000 / (nowTime - lastTime)).coerceAtLeast(0)
            txSpeed = ((nowTx - lastTx) * 1000 / (nowTime - lastTime)).coerceAtLeast(0)

            lastRx = nowRx
            lastTx = nowTx
            lastTime = nowTime
        }
    }

    val total = (rxSpeed + txSpeed).coerceAtLeast(1)
    val rxWeight = rxSpeed.toFloat() / total
    val txWeight = txSpeed.toFloat() / total

    Row(
        modifier = Modifier
            .wrapContentWidth()
            .height(25.dp)
    ) {
        Text(
            text = "↓ ${formatSpeed(rxSpeed)}",
            fontSize = if (rxSpeed >= txSpeed) 11.sp else 9.sp,
            fontWeight = if (rxSpeed >= txSpeed) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .weight(rxWeight)
                .fillMaxHeight()
        )
        Text(
            text = "↑ ${formatSpeed(txSpeed)}",
            fontSize = if (txSpeed > rxSpeed) 11.sp else 9.sp,
            fontWeight = if (txSpeed > rxSpeed) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .weight(txWeight)
                .fillMaxHeight()
        )
    }
}

private fun formatSpeed(speedBytes: Long): String {
    var value = speedBytes / 1024.0
    var unit = "KB/s"

    if (value >= 1024) {
        value /= 1024
        unit = "MB/s"
    }
    if (value >= 1024) {
        value /= 1024
        unit = "GB/s"
    }

    val displayValue = if (value < 10) "%.1f".format(value) else "%.0f".format(value)
    return "$displayValue $unit"
}
