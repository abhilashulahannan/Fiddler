package com.example.fiddler.subapps.Fidland.phs3.alarm

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
import kotlinx.coroutines.delay

/**
 * Phs3 module — Alarm.
 *
 * Qualifies for the State 3 rotation whenever an alarm is due to ring within
 * [ALARM_QUALIFY_WINDOW_MS] (30 min) — see [AlarmInfo.qualifies]. The hosting
 * service (FidlandService / a future AlarmPhs3Trigger) is responsible for
 * constructing this handler with the relevant [AlarmInfo] and calling
 * `activatePhs3(...)` / `deactivatePhs3()` as that window opens/closes.
 *
 * Indicator (State 3, right zone — first entity):
 *   [countdown text] [bell icon]
 *   Icon colour sweeps green → yellow → red as the alarm approaches,
 *   matching [iconStage]; in the last 5 minutes it also wiggles and shows
 *   small "ring" lines either side of the bell.
 *
 * ControlsPanel (State 5, entered by long-press):
 *   Header: bell icon in a tinted circle, alarm label, "rings in Xm Ys".
 *   Divider, then "alarm set for" / time row, divider, then
 *   Cancel / Snooze 5 min action buttons.
 *
 * @param alarmInfo   The upcoming alarm this instance represents.
 * @param onCancel    Invoked when the user taps "cancel alarm". Should clear
 *                     the system alarm and call `deactivatePhs3()`.
 * @param onSnooze    Invoked when the user taps "snooze 5 min". Should push
 *                     the system alarm back by 5 minutes.
 */
class AlarmPhs3Handler(
    private val alarmInfo: AlarmInfo,
    private val onCancel: () -> Unit = {},
    private val onSnooze: () -> Unit = {}
) : Phs3Handler {

    override val label: String = "Alarm"

    @Composable
    override fun Indicator() {
        val remainingMs = rememberRemainingMs(alarmInfo)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formatCountdown(remainingMs),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            AlarmClockIcon(remainingMs = remainingMs, size = 22.dp)
        }
    }

    @Composable
    override fun State5Content() {
        val remainingMs = rememberRemainingMs(alarmInfo)
        val stage = iconStage(remainingMs)

        var cancelled by remember { mutableStateOf(false) }
        var snoozeFlash by remember { mutableStateOf(false) }

        val iconBg = when {
            cancelled -> Color(0xFF1A2B1A)
            else -> when (stage) {
                AlarmIconStage.GREEN  -> Color(0xFF0F2B1A)
                AlarmIconStage.YELLOW -> Color(0xFF2B2500)
                AlarmIconStage.RED    -> Color(0xFF2B0F0F)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {

            // ── Header: icon + label + subtitle ─────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    AlarmClockIcon(
                        remainingMs = if (cancelled) Long.MAX_VALUE else remainingMs,
                        size = 20.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alarmInfo.label.ifBlank { "Alarm" },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (cancelled) "no upcoming alarms" else formatRingsIn(remainingMs),
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            Divider()

            // ── "alarm set for" / time row ──────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "alarm set for",
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
                Text(
                    text = formatClockTime(alarmInfo.triggerAtMs),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Divider()

            // ── Actions: cancel / snooze ────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // Cancel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (cancelled) Color(0xFF1A2B1A) else Color(0xFF2A1A1A))
                        .clickable(enabled = !cancelled) {
                            cancelled = true
                            onCancel()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (cancelled) "alarm cancelled" else "cancel alarm",
                        color = if (cancelled) Color(0xFF22C55E) else Color(0xFFFF3B30),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Snooze
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F1F1F))
                        .clickable(enabled = !cancelled) {
                            snoozeFlash = true
                            onSnooze()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (snoozeFlash) "+5 min added" else "snooze 5 min",
                        color = if (snoozeFlash) Color.White else Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Revert the snooze button label after 1.5s, mirroring the reference.
        LaunchedEffect(snoozeFlash) {
            if (snoozeFlash) {
                delay(1500L)
                snoozeFlash = false
            }
        }
    }
}

/** Thin divider matching the reference's `.s5-divider`. */
@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFF2A2A2A))
    )
}

/**
 * Ticking remaining-time state, updated once per second.
 * Recomposes [Indicator]/[ControlsPanel] every second without restarting
 * the whole composable tree.
 */
@Composable
private fun rememberRemainingMs(alarmInfo: AlarmInfo): Long {
    var remainingMs by remember(alarmInfo.triggerAtMs) {
        mutableLongStateOf(alarmInfo.remainingMs(System.currentTimeMillis()))
    }
    LaunchedEffect(alarmInfo.triggerAtMs) {
        while (true) {
            remainingMs = alarmInfo.remainingMs(System.currentTimeMillis())
            if (remainingMs <= 0L) break
            delay(1000L)
        }
    }
    return remainingMs
}

/** Formats an epoch-ms time as "9:15 AM" style clock time. */
private fun formatClockTime(epochMs: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = epochMs
    var hour = cal.get(java.util.Calendar.HOUR)
    if (hour == 0) hour = 12
    val minute = cal.get(java.util.Calendar.MINUTE)
    val amPm = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(hour, minute, amPm)
}