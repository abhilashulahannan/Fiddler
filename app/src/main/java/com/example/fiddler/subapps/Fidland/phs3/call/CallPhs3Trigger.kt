package com.example.fiddler.subapps.Fidland.phs3.call

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager
import com.example.fiddler.subapps.Fidland.phs3.Phs3Priority
import com.example.fiddler.subapps.Fidland.phs3.PriorityClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * CallPhs3Trigger
 *
 * Registers [ActiveCallPhs3Handler] and [MissedCallPhs3Handler] with
 * [Phs3Manager] based on live phone state and call-log changes.
 *
 * ── Active calls ──────────────────────────────────────────────────────────────
 * Uses [TelephonyCallback] (API 31+) or the deprecated [PhoneStateListener]
 * (below 31) to react to CALL_STATE_* transitions pushed by the OS.
 *
 *   IDLE    → unregister ActiveCallPhs3Handler
 *   RINGING → register with direction=INCOMING, connectionState=RINGING
 *   OFFHOOK → register with direction=OUTGOING (if we never saw RINGING first)
 *             or transition connectionState → ACTIVE (if we saw RINGING, so
 *             this is an accepted incoming call).
 *
 * The phone number is supplied by the telephony state callback on API < 31;
 * on API 31+ it is not available there (privacy restriction), so we fall back
 * to reading the most recent entry from CallLog.Calls immediately after
 * OFFHOOK/RINGING fires. The call log read is done on an IO dispatcher to
 * avoid blocking Main.
 *
 * ── Missed calls ──────────────────────────────────────────────────────────────
 * Registers a [ContentObserver] on [CallLog.Calls.CONTENT_URI]. Any change
 * triggers a re-query of unread missed calls (TYPE = MISSED, IS_READ = 0) from
 * the last 24 hours. Results are grouped per caller and handed to
 * [MissedCallPhs3Handler]. When the list is empty the handler is unregistered.
 *
 * ── Permissions required ──────────────────────────────────────────────────────
 *   • READ_PHONE_STATE  — for TelephonyCallback / PhoneStateListener
 *   • READ_CALL_LOG     — for missed-call queries + call-log name fallback
 *   • READ_CONTACTS     — for real-time Contacts lookup (see below)
 *
 * READ_PHONE_STATE, READ_CALL_LOG and READ_CONTACTS are all requested in
 * PermissionsActivity. If any is absent the trigger degrades gracefully:
 * missing READ_PHONE_STATE disables active-call tracking; missing
 * READ_CALL_LOG disables missed-call tracking; missing READ_CONTACTS just
 * means [resolveContact] falls back to the call-log's cached name (which
 * may be blank — see below).
 *
 * ── Caller-name resolution (fixed) ────────────────────────────────────────────
 * Previously [resolveContact] only read `CallLog.Calls.CACHED_NAME` for the
 * dialed/ringing number. That column is only populated once a call-log ROW
 * exists for that number — but on RINGING (an incoming call), the system
 * hasn't written a log row yet, so a caller's very first call (or any call
 * before the log commits) always showed the bare number, even for a saved
 * contact. `resolveContact` now queries `ContactsContract.PhoneLookup`
 * directly first — this is a live lookup against the Contacts provider, not
 * dependent on call-log timing — and only falls back to the call-log's
 * cached name if that lookup finds nothing (e.g. an unsaved number that
 * still has a cached name from a prior logged call).
 *
 * ── Mute / speaker / record wiring ────────────────────────────────────────────
 * [ActiveCallInfo.isMuted] / [isSpeakerOn] / [isRecording] are now tracked
 * per-call (the `isMuted`/`isSpeakerOn`/`isRecording` fields below) and
 * round-tripped into a freshly built [ActiveCallInfo] on every toggle, so
 * State 5's button grid reflects the actual state instead of always
 * resetting to "off".
 *
 * Caveat: this app is not the default dialer and does not bind an
 * InCallService, so it has no first-class API for mic-mute or call
 * recording. `toggleMute` best-efforts via `AudioManager.setMicrophoneMute`
 * (works on most stock/AOSP builds while a call is active; some OEM audio
 * HALs ignore it for telephony audio — test on target devices). `onRecord`
 * does not capture call audio itself — Android has not exposed a public API
 * for that since Android 10, and doing so without both parties' consent is
 * illegal in many jurisdictions — instead it launches the device's own
 * voice-recorder app, mirroring [RecordPhs3Handler]'s "open the real app"
 * pattern.
 *
 * ── Wire-up in FidlandService ────────────────────────────────────────────────
 *
 *   // Declaration (alongside other trigger lateinit vars):
 *   private lateinit var callTrigger: CallPhs3Trigger
 *
 *   // In onCreate(), after phs3Manager is created:
 *   callTrigger = CallPhs3Trigger(applicationContext, serviceScope, phs3Manager)
 *   callTrigger.start()
 *
 *   // In onDestroy():
 *   if (::callTrigger.isInitialized) callTrigger.stop()
 *
 * ── Import to add to FidlandService ──────────────────────────────────────────
 *   import com.example.fiddler.subapps.Fidland.phs3.call.CallPhs3Trigger
 *
 * ── §B6/§B7 wiring (this pass) — Call's first scheduler bids ────────────────
 * Design doc §B7 gives Call two independent, simultaneously-live bids —
 * [Phs3Scheduler] resolves between them (and against every other entity)
 * by class then sub-score, so no special-casing is needed here beyond
 * submitting the right numbers at the right moments:
 *
 * • **Active call — indefinite-hold Special Condition, sub-score 90.**
 *   Submitted (under the `"Call"` label — see [ActiveCallPhs3Handler]'s
 *   class doc on why that label, not `"ActiveCall"`) alongside every
 *   [registerActiveCall] call, i.e. on RINGING and on OFFHOOK — both count
 *   as "ringing/outgoing/in-progress" per §B7. `holdMs = null`: this is an
 *   indefinite hold, not a timed promotion, so there's nothing for
 *   [Phs3Priority.fallback] to do (ignored whenever `holdMs` is null — see
 *   its own doc) — `null` is passed rather than a placeholder. Because
 *   SPECIAL_CONDITION always outranks DOMINANT regardless of sub-score,
 *   this bid beats a concurrent missed-calls-Dominant bid automatically —
 *   §B7's "active-call's Special Condition holds regardless of missed-call
 *   state" falls out of the scheduler's own sort, not a manual check.
 * • **Missed calls — conditional Dominant, sub-score 50.** Submitted under
 *   the `"MissedCall"` label whenever [refreshMissedCalls] finds ≥1 unread
 *   missed call, withdrawn the instant the list empties — this is a
 *   persists-until-cleared condition, not a Special Condition (not time-
 *   bounded), same axis as Battery's low-battery escalation.
 * • **Home Submissive/10** is deliberately *not* submitted anywhere in this
 *   trigger. §B7 itself flags it as "rarely observed — nothing to show
 *   until qualified": [ActiveCallPhs3Handler] only ever exists while
 *   RINGING/OFFHOOK (i.e. already at the Special-Condition/90 bid above),
 *   and is fully unregistered — not merely low-priority — the instant
 *   CALL_STATE_IDLE fires. There is no qualified-but-idle state for this
 *   handler to hold a Submissive bid *in*, so one submitted here would
 *   either never win or reference a handler no longer in [manager]'s
 *   `qualified` list. [stop] and the CALL_STATE_IDLE branch below both
 *   [Phs3Scheduler.withdraw] `"Call"` outright rather than downgrading to a
 *   bid nothing would ever observe.
 * • **Block placement (§B2)** — the location-a icon / primary+secondary
 *   split lives entirely on [ActiveCallPhs3Handler]/[MissedCallPhs3Handler]
 *   via [com.example.fiddler.subapps.Fidland.phs3.Phs3Handler
 *   .hasSecondaryBlock] — see their class docs. Nothing here changes for
 *   that; this trigger only constructs handlers and submits priority bids.
 */
class CallPhs3Trigger(
    private val context: Context,
    private val scope: CoroutineScope,
    private val manager: Phs3Manager,
) {
    // ── Telephony ─────────────────────────────────────────────────────────────

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var telephonyCallback: TelephonyCallback? = null

    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    // ── Call-log observer ─────────────────────────────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())

    private val callLogObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            scope.launch { refreshMissedCalls() }
        }
    }

    // ── Active-call state ─────────────────────────────────────────────────────

    /** True once we have seen RINGING for the current call leg. */
    private var seenRinging = false

    /** Epoch-ms when the call transitioned to OFFHOOK (talk start). */
    private var talkStartMs: Long? = null

    // Remembered so the IDLE handler can look up the CallLog entry for the
    // call that just ended — see correctOutgoingCallDuration().
    private var lastCallNumber: String? = null
    private var lastCallDirection: CallDirection? = null

    private val _lastCallCorrection = MutableStateFlow<CallDurationCorrection?>(null)

    /**
     * Accurate post-call duration for the most recent OUTGOING call, once
     * CallLog commits it — null until the first outgoing call completes, and
     * never set for incoming calls (their live timer is already accurate,
     * see OFFHOOK's kdoc, so there's nothing to correct). Nothing currently
     * displays this — it's here so a future "last call" UI has an accurate
     * number to show without re-deriving it.
     */
    val lastCallCorrection: StateFlow<CallDurationCorrection?> = _lastCallCorrection.asStateFlow()

    // ── Toggle state for the current call ─────────────────────────────────────
    // Reset to false whenever a *new* call starts (see resetToggleState()).
    // Persisted across handleCallState transitions so State 5's button grid
    // reflects the real on/off state instead of resetting on every rebuild.
    private var isMuted = false
    private var isSpeakerOn = false
    private var isRecording = false

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun start() {
        Phs3DebugLog.onTriggerStart("Call")
        registerTelephonyListener()
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            /* notifyForDescendants = */ true,
            callLogObserver,
        )
        // Prime missed-call state immediately on start.
        scope.launch { refreshMissedCalls() }
    }

    fun stop() {
        Phs3DebugLog.onTriggerStop("Call")
        unregisterTelephonyListener()
        context.contentResolver.unregisterContentObserver(callLogObserver)
        manager.scheduler.withdraw("Call")
        manager.scheduler.withdraw("MissedCall")
        manager.unregister("Call")
        manager.unregister("MissedCall")
    }

    // ── Telephony listener (API-split) ────────────────────────────────────────

    private fun registerTelephonyListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    // Phone number is not provided to TelephonyCallback on API 31+
                    // (privacy change). We resolve it from the call log after the
                    // state transition.
                    scope.launch { handleCallState(state, phoneNumber = null) }
                }
            }
            telephonyCallback = callback
            try {
                ContextCompat.getMainExecutor(context).let { executor ->
                    telephonyManager.registerTelephonyCallback(executor, callback)
                }
            } catch (_: SecurityException) {
                Phs3DebugLog.onPoll("Call", "READ_PHONE_STATE denied — active call tracking disabled")
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                    scope.launch { handleCallState(state, phoneNumber = incomingNumber) }
                }
            }
            phoneStateListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    listener,
                    PhoneStateListener.LISTEN_CALL_STATE,
                )
            } catch (_: SecurityException) {
                Phs3DebugLog.onPoll("Call", "READ_PHONE_STATE denied — active call tracking disabled")
            }
        }
    }

    private fun unregisterTelephonyListener() {
        telephonyCallback?.let {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
            } catch (_: Exception) { /* already gone */ }
        }
        telephonyCallback = null

        @Suppress("DEPRECATION")
        phoneStateListener?.let {
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            } catch (_: Exception) { /* already gone */ }
        }
        phoneStateListener = null
    }

    // ── Active-call state machine ──────────────────────────────────────────────

    private suspend fun handleCallState(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // A fresh RINGING with nothing in progress means this is a new
                // call leg — clear any leftover toggle state from a previous
                // call so mute/speaker/record don't "leak" into the next one.
                if (!seenRinging && talkStartMs == null) resetToggleState()
                seenRinging = true
                talkStartMs = null
                val number = phoneNumber ?: resolveLastCallNumber() ?: "Unknown"
                val (display, canonical) = resolveContact(number)
                Phs3DebugLog.onPoll("Call", "RINGING number=$canonical display=$display")
                registerActiveCall(
                    displayName = display,
                    phoneNumber = canonical,
                    direction = CallDirection.INCOMING,
                    connectionState = CallConnectionState.RINGING,
                    talkStartMs = null,
                )
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Outgoing calls never pass through RINGING first, so this is
                // where a fresh outgoing call's toggle state gets reset.
                if (!seenRinging && talkStartMs == null) resetToggleState()
                // NOTE — timer accuracy caveat: OFFHOOK fires the moment this
                // device grabs the call audio focus, which for an OUTGOING
                // call is as soon as you dial — not when the other party
                // answers. TelephonyManager/PhoneStateListener has no event
                // for "remote party answered", so talkStartMs (and therefore
                // the duration timer) starts a few seconds early on outgoing
                // calls. This is correct and exact for INCOMING calls (OFFHOOK
                // here really does mean "the user just tapped Accept"). Getting
                // outgoing calls frame-accurate would require binding an
                // InCallService (Call.STATE_ACTIVE) and registering as a
                // default-dialer/calling-companion app — a much bigger change
                // than this trigger's scope.
                val startMs = System.currentTimeMillis()
                talkStartMs = startMs
                val number = phoneNumber ?: resolveLastCallNumber() ?: "Unknown"
                val (display, canonical) = resolveContact(number)
                val direction = if (seenRinging) CallDirection.INCOMING else CallDirection.OUTGOING
                lastCallNumber = canonical
                lastCallDirection = direction
                Phs3DebugLog.onPoll("Call", "OFFHOOK direction=$direction number=$canonical display=$display")
                registerActiveCall(
                    displayName = display,
                    phoneNumber = canonical,
                    direction = direction,
                    connectionState = CallConnectionState.ACTIVE,
                    talkStartMs = startMs,
                )
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Phs3DebugLog.onPoll("Call", "IDLE — unregistering ActiveCall")
                // Snapshot before resetting — needed below to correct the
                // outgoing-call timer against CallLog once it commits.
                val endedDirection = lastCallDirection
                val endedNumber = lastCallNumber
                val estimatedStartMs = talkStartMs
                val endedAtMs = System.currentTimeMillis()

                seenRinging = false
                talkStartMs = null
                lastCallNumber = null
                lastCallDirection = null
                resetToggleState()
                // §B7 — withdraw outright, not downgrade to home Submissive/10:
                // the handler is fully unregistered below too, so there's no
                // qualified-but-idle state for a low bid to mean anything in.
                // See class doc's "Home Submissive/10 is deliberately not
                // submitted" note. Withdrawing "Call" here is also what lets
                // a pending "MissedCall" Dominant/50 bid (if any) naturally
                // win the scheduler's next recompute — §B7's "falls back to
                // Dominant-if-missed-calls-pending else Submissive" falls out
                // of the scheduler's own class/sub-score sort, not a manual
                // fallback chain.
                manager.scheduler.withdraw("Call")
                manager.unregister("Call")
                // A call just ended — re-check for missed calls immediately,
                // since the log entry may not have been written yet. A short
                // delay gives the telephony stack time to commit the log row.
                delay(1_500)
                refreshMissedCalls()

                // Outgoing calls start their timer at OFFHOOK (dial time),
                // not remote-answer (see OFFHOOK's kdoc) — a few seconds
                // early. Incoming calls are already frame-accurate, so
                // there's nothing to correct there. Now that the call has
                // ended, CallLog.Calls.DURATION holds the OS's own accurate
                // talk time — it knows the real connect time even though we
                // never did — so use that to correct our estimate.
                if (endedDirection == CallDirection.OUTGOING &&
                    endedNumber != null && estimatedStartMs != null
                ) {
                    correctOutgoingCallDuration(endedNumber, estimatedStartMs, endedAtMs)
                }
            }
        }
    }

    /** Clears mute/speaker/record toggle state — call at the start of every new call leg. */
    private fun resetToggleState() {
        isMuted = false
        isSpeakerOn = audioManager.isSpeakerphoneOn
        isRecording = false
    }

    /**
     * Builds and registers an [ActiveCallPhs3Handler] for the current call,
     * stamping in the live [isMuted]/[isSpeakerOn]/[isRecording] toggle state
     * so State 5's button grid reflects reality instead of resetting to
     * "off" on every RINGING→ACTIVE transition.
     */
    private fun registerActiveCall(
        displayName: String?,
        phoneNumber: String,
        direction: CallDirection,
        connectionState: CallConnectionState,
        talkStartMs: Long?,
    ) {
        val handler = ActiveCallPhs3Handler(
            callInfo = ActiveCallInfo(
                displayName = displayName,
                phoneNumber = phoneNumber,
                direction = direction,
                connectionState = connectionState,
                talkStartMs = talkStartMs,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isRecording = isRecording,
            ),
            onEndCall = { endCall() },
            onMute = {
                isMuted = toggleMute()
                // Re-register (same label → replaces in-place, see
                // Phs3Manager.register) so the button grid repaints
                // immediately with the new toggle state.
                registerActiveCall(displayName, phoneNumber, direction, connectionState, talkStartMs)
            },
            onSpeaker = {
                isSpeakerOn = toggleSpeaker()
                registerActiveCall(displayName, phoneNumber, direction, connectionState, talkStartMs)
            },
            onRecord = {
                isRecording = openVoiceRecorder()
                registerActiveCall(displayName, phoneNumber, direction, connectionState, talkStartMs)
            },
            onAddCall = { openInCallUi() },
            onKeypad = { openInCallUi() },
        )
        manager.register(handler)

        // §B7 — ringing/outgoing/in-progress is an indefinite-hold Special
        // Condition, sub-score 90. holdMs = null, so fallback is unused
        // (ignored whenever holdMs is null) — see class doc.
        manager.scheduler.submit(
            Phs3Priority(
                handler       = handler,
                priorityClass = PriorityClass.SPECIAL_CONDITION,
                subScore      = 90,
                holdMs        = null,
                fallback      = null,
            )
        )
    }

    // ── Missed calls ──────────────────────────────────────────────────────────

    private suspend fun refreshMissedCalls() {
        val missed = queryMissedCalls()
        Phs3DebugLog.onPoll("Call", "missedCalls=${missed.size}")
        if (missed.isEmpty()) {
            manager.scheduler.withdraw("MissedCall")
            manager.unregister("MissedCall")
        } else {
            val handler = MissedCallPhs3Handler(
                missedCalls = missed,
                onCallBack  = { number -> dialNumber(number) },
            )
            manager.register(handler)
            // §B7 — ≥1 unread missed call is a conditional-Dominant
            // escalation, sub-score 50, persisting until cleared (not time-
            // bounded, so no holdMs/fallback — same axis as Battery's low-
            // battery escalation, not a Special Condition).
            manager.scheduler.submit(
                Phs3Priority(
                    handler       = handler,
                    priorityClass = PriorityClass.DOMINANT,
                    subScore      = 50,
                )
            )
        }
    }

    /**
     * Queries the call log for unread missed calls in the last 24 hours,
     * grouped by caller. Returns an empty list if READ_CALL_LOG is not
     * granted.
     */
    private suspend fun queryMissedCalls(): List<MissedCallInfo> =
        withContext(Dispatchers.IO) {
            val cutoffMs = System.currentTimeMillis() - 24 * 60 * 60 * 1_000L
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
            )
            val selection =
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}" +
                        " AND ${CallLog.Calls.IS_READ} = 0" +
                        " AND ${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(cutoffMs.toString())

            return@withContext try {
                val entries = mutableListOf<Triple<String, String?, Long>>() // number, name, timestamp
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CallLog.Calls.DATE} DESC",
                )?.use { cursor ->
                    val numIdx  = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    while (cursor.moveToNext()) {
                        val number    = cursor.getString(numIdx)  ?: continue
                        val name      = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                        val timestamp = cursor.getLong(dateIdx)
                        entries.add(Triple(number, name, timestamp))
                    }
                }

                // Group by number, preserve most-recent-first order within each group.
                entries
                    .groupBy { (number, _, _) -> number }
                    .map { (number, rows) ->
                        val displayName = rows.firstOrNull { it.second != null }?.second
                        MissedCallInfo(
                            displayName = displayName,
                            phoneNumber = number,
                            count       = rows.size,
                            entries     = rows.map { (n, name, ts) ->
                                MissedCallEntry(
                                    displayName = name,
                                    phoneNumber = n,
                                    timestampMs = ts,
                                )
                            },
                        )
                    }
                    .sortedByDescending { it.entries.firstOrNull()?.timestampMs ?: 0L }
            } catch (_: SecurityException) {
                // READ_CALL_LOG not granted.
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reads the most recent row from the call log to recover the phone number
     * when the telephony callback doesn't supply it (API 31+).
     * Returns null if READ_CALL_LOG is not granted or the log is empty.
     */
    private suspend fun resolveLastCallNumber(): String? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.NUMBER),
                    null, null,
                    "${CallLog.Calls.DATE} DESC LIMIT 1",
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (_: SecurityException) { null }
            catch (_: Exception)          { null }
        }

    /**
     * Corrects the just-ended outgoing call's duration against
     * `CallLog.Calls.DURATION`, which the OS computes from the real
     * telecom-stack connect time — accurate even though our own
     * [talkStartMs] (stamped at OFFHOOK/dial) runs a few seconds early for
     * outgoing calls (see OFFHOOK's kdoc). This only fixes the number
     * after the fact via [lastCallCorrection] — the live State5 timer
     * during the call keeps using the OFFHOOK-based estimate, since there's
     * no earlier moment to correct it from without an InCallService.
     *
     * Looks at the 5 most recent OUTGOING call-log rows (not just the
     * newest, in case a missed-call log row or another outgoing call from
     * a different number was written in between) and matches by comparing
     * the last 7 digits, same tolerance [querySmsForNumber] uses for phone
     * formatting differences. No-ops if READ_CALL_LOG isn't granted, the
     * log hasn't committed yet, or nothing matches.
     */
    private suspend fun correctOutgoingCallDuration(
        phoneNumber: String,
        estimatedStartMs: Long,
        endedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        val targetSuffix = phoneNumber.filter { it.isDigit() }.takeLast(7)
        if (targetSuffix.length < 7) return@withContext

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION),
                "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE}",
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 5",
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val durIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numIdx) ?: continue
                    if (number.filter { it.isDigit() }.takeLast(7) != targetSuffix) continue

                    val actualDurationMs = cursor.getLong(durIdx) * 1_000L
                    val estimatedDurationMs = (endedAtMs - estimatedStartMs).coerceAtLeast(0L)

                    Phs3DebugLog.onPoll(
                        "Call",
                        "OUTGOING duration correction number=$phoneNumber " +
                                "ourEstimate=${formatDuration(estimatedDurationMs)} " +
                                "osReported=${formatDuration(actualDurationMs)} " +
                                "diffMs=${estimatedDurationMs - actualDurationMs}",
                    )
                    _lastCallCorrection.value = CallDurationCorrection(
                        phoneNumber = phoneNumber,
                        estimatedDurationMs = estimatedDurationMs,
                        actualDurationMs = actualDurationMs,
                    )
                    return@withContext
                }
                // Log hasn't committed a matching row yet — not treated as
                // an error, just means the timing lost the race against
                // Telecom writing the row. Nothing to correct this call.
            }
        } catch (_: SecurityException) {
            // READ_CALL_LOG not granted — nothing to correct.
        } catch (_: Exception) {
            // Best-effort — leave lastCallCorrection at its previous value.
        }
    }

    /**
     * Resolves a display name for [rawNumber].
     * Returns Pair(displayName or null, canonicalNumber).
     *
     * Tries, in order:
     *   1. [ContactsContract.PhoneLookup] — a live query against the Contacts
     *      provider. This is what actually fixes "shows number, not the saved
     *      contact name" for incoming calls: it doesn't depend on a call-log
     *      row existing yet, so it works on the very first RINGING callback
     *      for a saved contact, not just calls that have already been logged.
     *      Requires READ_CONTACTS.
     *   2. The call log's `CACHED_NAME` for this number, as a fallback for
     *      numbers not saved as a contact but seen before. Requires
     *      READ_CALL_LOG.
     *
     * Falls back to (null, rawNumber) if neither permission is granted or
     * neither lookup finds a match — the UI then just shows the number.
     */
    private suspend fun resolveContact(rawNumber: String): Pair<String?, String> =
        withContext(Dispatchers.IO) {
            resolveContactFromContacts(rawNumber)?.let { return@withContext Pair(it, rawNumber) }
            val cachedName = try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(CallLog.Calls.CACHED_NAME),
                    "${CallLog.Calls.NUMBER} = ?",
                    arrayOf(rawNumber),
                    "${CallLog.Calls.DATE} DESC LIMIT 1",
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }
                    } else null
                }
            } catch (_: SecurityException) { null }
            catch (_: Exception)          { null }
            Pair(cachedName, rawNumber)
        }

    /**
     * Live lookup of [rawNumber] against the Contacts provider via
     * `ContactsContract.PhoneLookup`, which does Android's own
     * phone-number-normalization/matching under the hood (so formatting
     * differences like spaces, dashes, or a missing country code still
     * match a saved contact in most cases). Returns null if READ_CONTACTS
     * isn't granted, the number is blank, or no contact matches.
     */
    private fun resolveContactFromContacts(rawNumber: String): String? {
        if (rawNumber.isBlank() || rawNumber == "Unknown") return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (_: SecurityException) { null }
        catch (_: Exception)          { null }
    }

    // ── Call actions ──────────────────────────────────────────────────────────

    /**
     * Ends the active call via reflection on the hidden ITelephony binder.
     * This is the standard approach used by AOSP dialer apps that aren't the
     * default dialer. Falls back to opening the system in-call UI on failure.
     */
    private fun endCall() {
        try {
            val method = telephonyManager.javaClass.getDeclaredMethod("getITelephony")
            method.isAccessible = true
            val iTelephony = method.invoke(telephonyManager)
            iTelephony?.javaClass?.getDeclaredMethod("endCall")?.invoke(iTelephony)
        } catch (_: Exception) {
            openInCallUi()
        }
    }

    /**
     * Best-effort mic mute via [AudioManager.setMicrophoneMute]. Not a true
     * InCallService mute — this app isn't the default dialer and doesn't
     * bind one — but it's the standard non-default-dialer approach and works
     * on most stock/AOSP audio stacks while a call is in progress. Some OEM
     * audio HALs may ignore it for telephony audio specifically; if it turns
     * out to be unreliable on your target device, the fallback is opening
     * the system in-call UI ([openInCallUi]) so the user can mute there.
     * Returns the new mute state so the caller can update [ActiveCallInfo].
     */
    private fun toggleMute(): Boolean {
        val newState = !audioManager.isMicrophoneMute
        return try {
            audioManager.isMicrophoneMute = newState
            newState
        } catch (_: SecurityException) {
            openInCallUi()
            audioManager.isMicrophoneMute
        }
    }

    /** Returns the new speakerphone state so the caller can update [ActiveCallInfo]. */
    private fun toggleSpeaker(): Boolean {
        val newState = !audioManager.isSpeakerphoneOn
        audioManager.isSpeakerphoneOn = newState
        return newState
    }

    /**
     * Does NOT record the call itself — see the class doc for why that's
     * not something a non-system app can reliably (or legally, without
     * consent) do on modern Android. Instead this launches the device's own
     * voice-recorder app so the user can start a recording there, same
     * pattern as [com.example.fiddler.subapps.Fidland.phs3.record.RecordPhs3Handler]'s
     * "Open Recorder" button. Tries a handful of common recorder package
     * names first (so it opens directly into recording rather than a picker);
     * falls back to a chooser for a generic audio-capable intent if none are
     * installed. Returns true if an app was successfully launched, so the
     * caller can reflect a lightweight "recording" state in the button —
     * this is a UI hint, not a guarantee that recording actually started.
     */
    private fun openVoiceRecorder(): Boolean {
        val knownRecorderPackages = listOf(
            "com.google.android.apps.recorder",       // Pixel Recorder
            "com.sec.android.app.voicenote",           // Samsung Voice Recorder
            "com.coloros.soundrecorder",                // ColorOS
            "com.miui.notes",                           // MIUI (Voice memo lives in Notes on some builds)
            "com.android.soundrecorder",                // AOSP reference recorder
        )
        for (pkg in knownRecorderPackages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                return try { context.startActivity(intent); true } catch (_: Exception) { false }
            }
        }
        // No known recorder installed — offer a generic "record audio" chooser.
        return try {
            val intent = android.content.Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) { false }
    }

    private fun dialNumber(number: String) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_CALL,
            Uri.parse("tel:$number"),
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { /* permission denied or no dialer */ }
    }

    private fun openInCallUi() {
        // ACTION_DIAL with no data brings up the in-call / dialer UI on all
        // Android versions without needing a specific category or permission.
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) } catch (_: Exception) { }
    }
}