package com.example.fiddler.subapps.Fidland.phs3.ringmode

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager

/**
 * RingmodePhs3Trigger
 *
 * Keeps [VolumePhs3Handler] registered with [Phs3Manager] at all times
 * (ring mode is always relevant), updating it whenever the ringer mode or
 * DND interruption filter changes.
 *
 * ── Sources of change ─────────────────────────────────────────────────────────
 * 1. [AudioManager.RINGER_MODE_CHANGED_ACTION] — broadcast when the user or
 *    system switches between Ring / Vibrate / Silent.
 * 2. [NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED] — broadcast
 *    when DND is toggled or its policy changes (Priority / Alarms / Silence).
 *
 * Both are normal (non-dangerous) permission broadcasts — no runtime
 * permission needed beyond what FidlandService already holds.
 *
 * ── Applying mode changes ─────────────────────────────────────────────────────
 * When [VolumePhs3Handler.onModeSelected] fires:
 *   • Ring / Vibrate / Silent → [AudioManager.setRingerMode].
 *   • DND → [NotificationManager.setInterruptionFilter] with PRIORITY,
 *            which is the most permissive DND level and least disruptive
 *            as a default. Requires [NotificationManager.isNotificationPolicyAccessGranted].
 *            If the grant is absent, we open the system DND settings so the
 *            user can grant it once. DND is then disabled (filter set to ALL)
 *            when the user selects Ring/Vibrate/Silent.
 *
 * ── Permissions ───────────────────────────────────────────────────────────────
 * No new manifest permissions needed. DND control uses
 * ACCESS_NOTIFICATION_POLICY (already in the manifest) + a one-time user
 * grant via Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   // Declaration:
 *   private lateinit var ringmodeTrigger: RingmodePhs3Trigger
 *
 *   // In onCreate(), after phs3Manager is created:
 *   ringmodeTrigger = RingmodePhs3Trigger(applicationContext, phs3Manager)
 *   ringmodeTrigger.start()
 *
 *   // In onDestroy():
 *   if (::ringmodeTrigger.isInitialized) ringmodeTrigger.stop()
 *
 * ── Import to add to FidlandService ──────────────────────────────────────────
 *   import com.example.fiddler.subapps.Fidland.phs3.ringmode.RingmodePhs3Trigger
 */
class RingmodePhs3Trigger(
    private val context: Context,
    private val manager: Phs3Manager,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val nm    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            Phs3DebugLog.onPoll("Ringmode", "broadcast: ${intent?.action}")
            push()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun start() {
        Phs3DebugLog.onTriggerStart("Ringmode")

        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        // Push the initial state immediately.
        push()
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Ringmode")
        try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        manager.unregister("Volume")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Builds a fresh snapshot and (re)registers the handler. */
    private fun push() {
        val snapshot = buildSnapshot(audio, nm)
        Phs3DebugLog.onPoll(
            "Ringmode",
            "mode=${snapshot.mode} dnd=${snapshot.dndPolicy} vol=${snapshot.ringerVolume}/${snapshot.ringerMaxVolume}"
        )
        manager.register(
            VolumePhs3Handler(
                snapshot       = snapshot,
                onModeSelected = { mode -> applyMode(mode) },
            )
        )
    }

    /**
     * Applies the user-selected [mode] to the system:
     * - Ring/Vibrate/Silent → [AudioManager.setRingerMode], also clears DND.
     * - DND → enables DND with Priority-only interruption filter (most
     *         permissive DND policy, lets alarms + priority contacts through).
     *         Requires a one-time user grant; if absent, opens system settings.
     */
    private fun applyMode(mode: RingMode) {
        when (mode) {
            RingMode.RING -> {
                clearDndIfNeeded()
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            RingMode.VIBRATE -> {
                clearDndIfNeeded()
                audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            RingMode.SILENT -> {
                clearDndIfNeeded()
                audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
            RingMode.DND -> {
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else {
                    // Grant not yet given — send user to system settings once.
                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) { }
                }
            }
        }
        // push() will be triggered automatically by the broadcast receiver
        // once the system applies the change. No need to call it manually.
    }

    /** Lifts DND back to ALL if it is currently active. No-op otherwise. */
    private fun clearDndIfNeeded() {
        if (!nm.isNotificationPolicyAccessGranted) return
        val filter = nm.currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}