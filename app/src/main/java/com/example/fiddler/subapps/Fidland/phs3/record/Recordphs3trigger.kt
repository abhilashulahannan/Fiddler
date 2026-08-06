package com.example.fiddler.subapps.Fidland.phs3.record

import android.content.Context
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * RecordPhs3Trigger
 *
 * Watches [RecorderNotificationSource.flow] and registers / unregisters
 * [RecordPhs3Handler] with [Phs3Manager] based on whether the phone's
 * recorder app is actively recording.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var recorderSource:  RecorderNotificationSource
 *   private lateinit var recorderTrigger: RecordPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       recorderSource  = RecorderNotificationSource()
 *       NotificationListenerService.recorderSource = recorderSource   // ← hook
 *       recorderTrigger = RecordPhs3Trigger(
 *           scope   = serviceScope,
 *           source  = recorderSource,
 *           manager = phs3Manager,
 *           context = this,
 *       )
 *       recorderTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       recorderTrigger.stop()
 *       NotificationListenerService.recorderSource = null
 *       ...
 *   }
 *
 * ── §B7 wiring (this pass) — Record's scheduler bid ──────────────────────────
 * Class: Special Condition, sub-score 80, held for the entire recording
 * (`holdMs = null` — indefinite, matching Battery's <5%-style indefinite
 * hold shape, minus [Phs3Priority.isHardOverride]: Record still competes on
 * class/score against another genuine Special Condition, it just never
 * times out on its own — the recorder ending is what clears it). No
 * `fallback` — irrelevant whenever `holdMs` is null.
 *
 * **Home Submissive bid is deliberately not submitted anywhere here** —
 * same reasoning as Timer's/Call's home bid: [RecordPhs3Handler] only ever
 * exists while [RecorderNotificationSource.flow]'s snapshot is active, i.e.
 * already at the Special-Condition bid below, and is fully unregistered the
 * instant it isn't — there's no qualified-but-idle state for a Submissive
 * bid to hold *in*.
 */
class RecordPhs3Trigger(
    private val scope:   CoroutineScope,
    private val source:  RecorderNotificationSource,
    private val manager: Phs3Manager,
    context: Context,
) {
    private val handler  = RecordPhs3Handler(source, context)
    private var watchJob: Job? = null

    fun start() {
        watchJob = scope.launch {
            source.flow.collect { snapshot ->
                if (snapshot.isActive) {
                    manager.register(handler)
                    // §B7 — Special Condition, sub-score 80, held for the
                    // entire recording (holdMs = null → indefinite, no
                    // fallback needed).
                    manager.scheduler.submit(
                        Phs3Priority(
                            handler       = handler,
                            priorityClass = PriorityClass.SPECIAL_CONDITION,
                            subScore      = 80,
                            holdMs        = null,
                        )
                    )
                } else {
                    manager.scheduler.withdraw(handler.label)
                    manager.unregister(handler.label)
                }
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        manager.scheduler.withdraw(handler.label)
        manager.unregister(handler.label)
    }
}