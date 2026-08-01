package com.example.fiddler.subapps.Fidland.phs3.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.service.FidlandService

/**
 * BatteryPhs3Trigger
 *
 * Registers a receiver for [Intent.ACTION_BATTERY_CHANGED] and activates /
 * deactivates the Battery phs3 slot as the level and charging state change.
 * Same state/trigger/handler split as [com.example.fiddler.subapps.Fidland.phs3.camera.CameraPhs3Trigger].
 *
 * ── Why ACTION_BATTERY_CHANGED, not a periodic poll ───────────────────────────
 * [Intent.ACTION_BATTERY_CHANGED] is a *sticky* broadcast — registering for
 * it returns the current battery state immediately (no need to wait for the
 * next change), and the system re-delivers it on every subsequent level or
 * plug-state change without any polling loop or wakelock on our part.
 *
 * ── Display trigger ────────────────────────────────────────────────────────
 * See [BatteryInfo.isActive] / the kdoc on [BatteryInfo] for the resolved
 * "when does this show" decision (charging, or low + discharging).
 *
 * ── Permissions ────────────────────────────────────────────────────────────
 * None required — [Intent.ACTION_BATTERY_CHANGED] is a normal sticky
 * broadcast, same tier as the other phs3 broadcast receivers (Ringmode).
 *
 * ── Wire-up in FidlandService ──────────────────────────────────────────────
 * Not yet wired into FidlandService — like CameraPhs3Trigger / CommsAggregator,
 * it ships as a self-contained, documented module rather than editing that
 * file directly:
 *
 *   private lateinit var batteryTrigger: BatteryPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       batteryTrigger = BatteryPhs3Trigger(applicationContext, this)
 *       batteryTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       batteryTrigger.stop()
 *       ...
 *   }
 *
 * ── Debugging ──────────────────────────────────────────────────────────────
 * Logs to Phs3DebugLog (visible in the Debugging screen): trigger
 * start/stop, and one POLL entry per battery broadcast showing level,
 * charging state, and whether the slot is active.
 */
class BatteryPhs3Trigger(
    private val context: Context,
    private val service: FidlandService,
) {
    // Stateless handler — just an icon, so one instance is reused for the
    // whole trigger lifetime (same pattern as CameraPhs3Handler).
    private val handler = BatteryPhs3Handler()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            publish(intent)
        }
    }

    fun start() {
        Phs3DebugLog.onTriggerStart("Battery")
        // ACTION_BATTERY_CHANGED is sticky — context.registerReceiver(receiver, filter)
        // returns the current battery intent immediately, so no separate
        // "read initial state" call is needed before start() returns.
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Battery")
        try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        service.deactivatePhs3(handler.label)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun publish(intent: Intent) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val level = if (rawLevel >= 0 && rawScale > 0) {
            (rawLevel * 100) / rawScale
        } else {
            100
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val isCharging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)

        val info = BatteryInfo(level = level, isCharging = isCharging)
        Phs3DebugLog.onPoll("Battery", "level=$level charging=$isCharging active=${info.isActive}")

        if (info.isActive) {
            service.activatePhs3(BatteryPhs3Handler(batteryInfo = info))
        } else {
            service.deactivatePhs3(handler.label)
        }
    }
}