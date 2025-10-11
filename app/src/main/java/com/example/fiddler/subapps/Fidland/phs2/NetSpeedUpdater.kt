package com.example.fiddler.subapps.Fidland.phs2

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.TrafficStats
import kotlinx.coroutines.delay

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
            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val nowTime = System.currentTimeMillis()

            rxSpeed = ((nowRx - lastRx) * 1000 / (nowTime - lastTime)).coerceAtLeast(0L)
            txSpeed = ((nowTx - lastTx) * 1000 / (nowTime - lastTime)).coerceAtLeast(0L)

            lastRx = nowRx
            lastTx = nowTx
            lastTime = nowTime
        }
    }

    val rxStronger = rxSpeed >= txSpeed
    val dlWeight = if (rxStronger) 0.6f else 0.4f
    val ulWeight = 1f - dlWeight

    Row(
        modifier = Modifier
            .height(25.dp)
            .wrapContentWidth()
    ) {
        Text(
            text = formatSpeed(rxSpeed, "↓"),
            fontSize = if (rxStronger) 10.sp else 8.sp,
            fontWeight = if (rxStronger) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(dlWeight).fillMaxHeight()
        )
        Text(
            text = formatSpeed(txSpeed, "↑"),
            fontSize = if (!rxStronger) 10.sp else 8.sp,
            fontWeight = if (!rxStronger) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(ulWeight).fillMaxHeight()
        )
    }
}

private fun formatSpeed(speedBytes: Long, arrow: String): String {
    var value = speedBytes / 1024.0
    var unit = "KB/s"
    if (value >= 1024) { value /= 1024; unit = "MB/s" }
    if (value >= 1024) { value /= 1024; unit = "GB/s" }

    val displayValue = if (value < 10) "%.1f".format(value) else "%.0f".format(value)
    return "$arrow$displayValue $unit"
}
