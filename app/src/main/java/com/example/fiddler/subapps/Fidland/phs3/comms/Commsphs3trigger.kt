package com.example.fiddler.subapps.Fidland.phs3.comms

import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.Phs3Scheduler
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import com.example.fiddler.subapps.Fidland.service.FidlandService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * CommsPhs3Trigger
 *
 * Drives the Comms phs3 slot from [CommsAggregator], which merges Bluetooth,
 * WiFi, NFC, and Cellular state into one [CommsSnapshot].
 *
 * ── §B7 wiring (this pass) — from "not wired" to a real dwell-and-fallback bid ──
 * Previous version just called [FidlandService.activatePhs3]/[FidlandService
 * .deactivatePhs3] whenever [CommsSnapshot.hasAnythingToShow] flipped — no
 * [Phs3Priority] was ever submitted, and since cellular service alone
 * satisfies `hasAnythingToShow()` on almost any phone, that made Comms
 * behave like a near-permanent occupant instead of the "cleanest
 * event-driven case in the doc" §B7 describes. This pass replaces that with
 * the actual spec'd shape:
 *
 * **Special Condition, sub-score [SPECIAL_CONDITION_SUB_SCORE] (50)** — any
 * *qualifying* change (WiFi connect/disconnect/band-change, Bluetooth
 * connect/disconnect, cellular network-type change, NFC on/off — see
 * [isQualifyingChange]) takes the slot for
 * [Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS]. Non-qualifying
 * emissions (signal-bar jitter, identical-value redelivery, SSID-unchanged
 * reconnects) update the displayed snapshot without re-triggering the
 * window, per [isQualifyingChange]'s diff against [lastFingerprint].
 *
 * **Dwell is self-managed, not [Phs3Priority.holdMs] + [Phs3Priority
 * .fallback]:** the doc's own Qualify note says Comms "falls back to
 * Submissive with no slot, no display" — i.e. the nominal home
 * Submissive/25 class from §B7's Class section is never actually visible;
 * once the window ends Comms disqualifies outright rather than sitting at
 * a real Submissive tier. Using `holdMs`/`fallback` would hand the
 * scheduler a real (if low-priority) Submissive bid that could legitimately
 * win and display when nothing else is qualified — the opposite of "no
 * slot, no display." So instead: submit with `holdMs = null` (same
 * condition-based-ending shape as Camera/Flashlight/Idle) and run our own
 * [dwellJob], restarted on every qualifying change — §B8's open "does a
 * second change mid-window extend it or start a new one" question,
 * resolved here as **extend**, since a fresh [dwellJob] launch on every
 * qualifying change is what a restart naturally does; picked, not
 * doc-specified, same as [Phs3Scheduler]'s 6.5s dwell default itself.
 * When [dwellJob] completes uninterrupted, [endWindow] withdraws the bid
 * and unregisters — the literal "no slot, no display."
 *
 * ── Co-display, not exclusive takeover ────────────────────────────────────
 * [CommsPhs3Handler.coDisplay] = true (see comms.kt) means
 * `overlay_fidland_pill.kt` renders Comms's icon additively alongside
 * whatever else currently holds the slot — this trigger does not call
 * [FidlandService.phs3Manager]'s `surfaceEventDriven`/`resumeAfterEventDriven`
 * the way an exclusive-takeover entity (Idle, Call) would; registering via
 * [FidlandService.activatePhs3] is sufficient for it to appear.
 *
 * ── Not yet addressed by this pass ────────────────────────────────────────
 * The single-composite-Lottie visual (one asset with named markers/layers,
 * in-place color transitions) is a design-asset dependency, not something
 * this trigger/handler pair can build — see design doc §B7 Comms / Phase 6.
 *
 * Activates whenever a qualifying change makes [CommsSnapshot
 * .hasAnythingToShow] true. Consider gating this behind a "phs3_comms"
 * preference toggle the same way Flashlight/Alarm/Nav are gated in
 * FidlandService.onCreate, since users may not want Comms interrupting
 * rotation on every WiFi hop.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   private lateinit var commsTrigger: CommsPhs3Trigger
 *
 *   override fun onCreate() {
 *       ...
 *       commsTrigger = CommsPhs3Trigger(applicationContext, serviceScope, this)
 *       if (prefs.getBoolean("phs3_comms", false))
 *           commsTrigger.start()
 *   }
 *
 *   override fun onDestroy() {
 *       commsTrigger.stop()
 *       ...
 *   }
 */
class CommsPhs3Trigger(
    context: android.content.Context,
    private val scope: CoroutineScope,
    private val service: FidlandService,
) {
    val aggregator = CommsAggregator(context, scope)

    private var collectJob: Job? = null
    private var dwellJob: Job? = null

    /** True while Comms currently holds a live Special-Condition window. */
    private var isSurfacing = false

    /** Qualifying-relevant fields from the last emission — see [isQualifyingChange]. */
    private var lastFingerprint: QualifyingFingerprint? = null

    /** The handler instance most recently pushed via [FidlandService.activatePhs3]. */
    private var currentHandler: CommsPhs3Handler? = null

    fun start() {
        Phs3DebugLog.onTriggerStart("Comms")
        aggregator.start()

        collectJob = aggregator.snapshot
            .onEach { snapshot -> onSnapshot(snapshot) }
            .launchIn(scope)
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Comms")
        collectJob?.cancel()
        collectJob = null
        dwellJob?.cancel()
        dwellJob = null
        isSurfacing = false
        lastFingerprint = null
        currentHandler = null
        service.phs3Manager.scheduler.withdraw("Comms")
        service.deactivatePhs3("Comms")
        aggregator.stop()
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun onSnapshot(snapshot: CommsSnapshot) {
        val fingerprint = snapshot.fingerprint()
        val prev = lastFingerprint
        lastFingerprint = fingerprint

        if (!snapshot.hasAnythingToShow()) {
            // Everything off (or airplane mode with nothing manually
            // re-enabled) — nothing qualifying can be currently displayed.
            if (isSurfacing) endWindow()
            return
        }

        val isQualifying = prev == null || isQualifyingChange(prev, fingerprint)

        // Always push the freshest snapshot while a window is open (or on
        // the qualifying change that opens one) so signal bars / connected-
        // device text stay live — only *whether to (re)start the dwell
        // timer* depends on isQualifying, per class doc.
        if (isQualifying || isSurfacing) {
            currentHandler = CommsPhs3Handler(snapshot)
            service.activatePhs3(currentHandler!!)
        }

        if (isQualifying) {
            startOrExtendWindow()
        }
    }

    private fun startOrExtendWindow() {
        Phs3DebugLog.onPoll("Comms", "qualifying change — (re)starting Special Condition dwell")
        isSurfacing = true

        service.phs3Manager.scheduler.submit(
            Phs3Priority(
                handler = currentHandler ?: return,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore = SPECIAL_CONDITION_SUB_SCORE,
                holdMs = null, // self-managed dwell — see class doc
            )
        )

        dwellJob?.cancel()
        dwellJob = scope.launch {
            delay(Phs3Scheduler.DEFAULT_SPECIAL_CONDITION_DWELL_MS)
            endWindow()
        }
    }

    private fun endWindow() {
        Phs3DebugLog.onPoll("Comms", "dwell elapsed — disqualifying, no persistent Submissive display")
        isSurfacing = false
        dwellJob?.cancel()
        dwellJob = null
        service.phs3Manager.scheduler.withdraw("Comms")
        service.deactivatePhs3("Comms")
    }

    private companion object {
        /** §B7 — Special Condition tier for any qualifying radio-state change. */
        const val SPECIAL_CONDITION_SUB_SCORE = 50
    }
}

/**
 * The subset of [CommsSnapshot] that counts as a "qualifying" change per
 * §B7's Comms entry — WiFi connect/disconnect/band-change, Bluetooth
 * connect/disconnect, cellular network-type change, NFC on/off (plus
 * airplane-mode toggling, not enumerated in the doc but the same shape of
 * event). Deliberately excludes signal-bar/RSSI/carrier-name fields, whose
 * fluctuation the doc calls out as explicitly non-qualifying.
 */
private data class QualifyingFingerprint(
    val btEnabled: Boolean,
    val btConnected: Boolean,
    val wifiEnabled: Boolean,
    val wifiSsid: String?,
    val wifiBand: WifiBand?,
    val nfcEnabled: Boolean,
    val cellularGeneration: CellularGeneration,
    val cellularHasService: Boolean,
    val airplaneModeOn: Boolean,
)

private fun CommsSnapshot.fingerprint() = QualifyingFingerprint(
    btEnabled = bluetooth.isEnabled,
    btConnected = bluetooth.connectedDevice != null,
    wifiEnabled = wifi.isEnabled,
    wifiSsid = wifi.ssid,
    wifiBand = wifi.band,
    nfcEnabled = nfc.isEnabled,
    cellularGeneration = cellular.generation,
    cellularHasService = cellular.hasService,
    airplaneModeOn = airplaneModeOn,
)

private fun isQualifyingChange(prev: QualifyingFingerprint, next: QualifyingFingerprint): Boolean =
    prev != next