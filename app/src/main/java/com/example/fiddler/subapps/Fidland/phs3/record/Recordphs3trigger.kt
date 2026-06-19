package com.example.fiddler.subapps.Fidland.phs3.record

import android.content.Context
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager
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
                } else {
                    manager.unregister(handler.label)
                }
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        manager.unregister(handler.label)
    }
}