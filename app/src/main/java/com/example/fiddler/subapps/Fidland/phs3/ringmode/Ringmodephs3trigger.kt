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
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass

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
 *
 * ── Naming ─────────────────────────────────────────────────────────────────
 * Every file/comment in this module says "Ringmode", but [VolumePhs3Handler]'s
 * actual `label = "Volume"` is the real identity key (logging, dashboard-tab
 * matching, scheduler bid routing). This is a file/doc naming inconsistency,
 * not a functional bug — left as-is rather than renamed here, since renaming
 * the label would ripple into dashboard tab-matching and any other
 * label-keyed lookups outside this module's own files.
 *
 * ── §B6/§B1 wiring (this pass) — home Dominant/15 + Special-Condition/55 ────
 * Design doc §B7: home Dominant, sub-score **15** (confirmed) — submitted on
 * every [push], continuous while Dominant (no EVENT_DRIVEN interrupt needed;
 * Ring Mode isn't wired into [Phs3Manager.policyOf], so it just rides
 * continuous rotation like every other entity besides Battery/Camera/
 * Flashlight). On top of that, a ring-mode **change** promotes to
 * SPECIAL_CONDITION for [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS]
 * (the doc's own 5-8s convention, sub-score in the confirmed **~55 tier**),
 * then automatically reverts to the home-15 bid via [Phs3Priority.fallback]
 * — Ring Mode is the first entity in this codebase to actually exercise
 * that field, rather than the scheduler's dwell/fallback mechanism sitting
 * unused.
 * • Change detection is a **snapshot diff against the last-registered
 *   mode**, not new infra — [push] already only fires on the two broadcasts
 *   registered in [start], so a push already means "something changed";
 *   this just distinguishes *which* field changed from the RingmodeSnapshot
 *   already being rebuilt every push.
 * • ⚠ Open per §B7 (not resolved here): does a DND sub-policy change (e.g.
 *   Priority Only → Alarms Only) with the top-level mode staying DND count
 *   as a "change" for this promotion? This implementation says **yes** —
 *   it diffs on `(mode, dndPolicy)` together, not `mode` alone — but the doc
 *   flags both interpretations as equally cheap to build, so this choice is
 *   a placeholder, not a settled call.
 *
 * ── Block placement (§B2) ─────────────────────────────────────────────────────
 * The two-block split (active-mode icon = dynamic, ring-mode text =
 * right/fixed) is wired on [VolumePhs3Handler] itself via
 * [Phs3Handler.hasSecondaryBlock] — see its class doc. Nothing in this
 * trigger needed to change: it only constructs [VolumePhs3Handler] and
 * submits priority bids, and the split lives entirely in how that handler
 * declares/renders its blocks.
 */
class RingmodePhs3Trigger(
    private val context: Context,
    private val manager: Phs3Manager,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val nm    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** §B7's confirmed home-class bid, rebuilt fresh per [push] with the current handler instance. */
    private fun homePriority(handler: VolumePhs3Handler) = Phs3Priority(
        handler       = handler,
        priorityClass = PriorityClass.DOMINANT,
        subScore      = 15,
    )

    /** Last snapshot's (mode, dndPolicy) pair — used to detect a genuine change vs. a re-push of the same state. */
    private var lastModeKey: Pair<RingMode, DndPolicy>? = null

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
        manager.scheduler.withdraw("Volume")
        manager.unregister("Volume")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Builds a fresh snapshot, (re)registers the handler, and submits §B7's priority bid(s). */
    private fun push() {
        val snapshot = buildSnapshot(audio, nm)
        Phs3DebugLog.onPoll(
            "Ringmode",
            "mode=${snapshot.mode} dnd=${snapshot.dndPolicy} vol=${snapshot.ringerVolume}/${snapshot.ringerMaxVolume}"
        )
        val handler = VolumePhs3Handler(
            snapshot       = snapshot,
            onModeSelected = { mode -> applyMode(mode) },
        )
        manager.register(handler)

        val home = homePriority(handler)
        val modeKey = snapshot.mode to snapshot.dndPolicy
        if (lastModeKey != null && lastModeKey != modeKey) {
            // Genuine ring-mode change (not the initial push) — promote to
            // Special Condition for the standard 5-8s dwell, then auto-revert
            // to the home-15 bid via fallback. See class doc.
            manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.SPECIAL_CONDITION,
                    subScore      = 55,
                    holdMs        = Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS,
                    fallback      = home,
                )
            )
        } else {
            manager.scheduler.submit(home)
        }
        lastModeKey = modeKey
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