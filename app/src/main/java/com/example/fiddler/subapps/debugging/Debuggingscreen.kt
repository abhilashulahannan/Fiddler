package com.example.fiddler.subapps.debugging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.R
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog

@Composable
fun DebuggingScreen() {
    val bodyFont = FontFamily(Font(R.font.font_body))
    val handFont = FontFamily(Font(R.font.font_handwriting))
    val scrollState = rememberScrollState()

    var logText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "Debugging",
            fontSize = 48.sp,
            fontFamily = bodyFont,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Internal logs and diagnostics",
            fontSize = 18.sp,
            fontFamily = handFont,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // ── Phs3 Entity Log ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Phs3 Entity Log",
            fontSize = 28.sp,
            fontFamily = bodyFont,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Tracks every register, unregister, trigger start/stop, and poll " +
                    "for all phs3 entities. Each event is also printed to logcat in real time.\n" +
                    "Logcat filter:  adb logcat -s Phs3DebugLog",
            fontSize = 14.sp,
            fontFamily = handFont,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { logText = Phs3DebugLog.dump() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Dump Log", fontFamily = handFont, fontSize = 15.sp)
            }
            OutlinedButton(
                onClick = { Phs3DebugLog.clear(); logText = "" },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear", fontFamily = handFont, fontSize = 15.sp)
            }
        }

        if (logText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = logText,
                    fontSize = 11.sp,
                    fontFamily = handFont,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "No log yet — tap Dump Log",
                        fontSize = 13.sp,
                        fontFamily = handFont,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}