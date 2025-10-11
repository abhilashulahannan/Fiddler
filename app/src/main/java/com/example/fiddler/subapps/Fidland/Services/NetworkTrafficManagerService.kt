package com.example.fiddler.subapps.Fidland.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

class NetworkTrafficManager(
    private val updateInterval: Long = 1000L // milliseconds
) {
    private var job: Job? = null

    var uploadSpeed by mutableStateOf("0 KB/s")
        private set

    var downloadSpeed by mutableStateOf("0 KB/s")
        private set

    /** Start updating network speeds */
    fun start() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val (up, down) = fetchNetworkSpeed() // Implement this
                uploadSpeed = up
                downloadSpeed = down
                delay(updateInterval)
            }
        }
    }

    /** Stop updating */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Enable or disable (start/stop) */
    fun toggle(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    /** Replace with real network logic */
    private fun fetchNetworkSpeed(): Pair<String, String> {
        // Fake data for now
        return (("↑ ${ (0..100).random() } KB/s") to ("↓ ${ (0..100).random() } KB/s"))
    }
}

@Composable
fun NetworkTrafficDisplay(
    networkManager: NetworkTrafficManager,
    enabled: Boolean
) {
    LaunchedEffect(enabled) {
        networkManager.toggle(enabled)
    }

    if (enabled) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = networkManager.uploadSpeed, fontSize = 9.sp, color = Color.White)
            Text(text = networkManager.downloadSpeed, fontSize = 9.sp, color = Color.White)
        }
    }
}
