package com.example.fiddler.subapps.Fidland.phs3.call

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.fiddler.subapps.Fidland.phs3.Phs3DebugLog
import com.example.fiddler.subapps.Fidland.phs3.Phs3Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
 *   • READ_CALL_LOG     — for call-log queries (missed calls + number lookup)
 *
 * READ_PHONE_STATE is already in PermissionsActivity. READ_CALL_LOG must be
 * added to the permission chain there alongside CALL_PHONE (they share the
 * same dangerous permission group, so the user sees one prompt). If either
 * permission is absent the trigger degrades gracefully: missing READ_PHONE_STATE
 * disables active-call tracking; missing READ_CALL_LOG disables missed-call
 * tracking and number lookup.
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
        manager.unregister("ActiveCall")
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
                seenRinging = true
                talkStartMs = null
                val number = phoneNumber ?: resolveLastCallNumber() ?: "Unknown"
                val (display, canonical) = resolveContact(number)
                Phs3DebugLog.onPoll("Call", "RINGING number=$canonical display=$display")
                manager.register(
                    ActiveCallPhs3Handler(
                        callInfo = ActiveCallInfo(
                            displayName = display,
                            phoneNumber = canonical,
                            direction = CallDirection.INCOMING,
                            connectionState = CallConnectionState.RINGING,
                            talkStartMs = null,
                        ),
                        onEndCall = { endCall() },
                        onMute    = { toggleMute() },
                        onSpeaker = { toggleSpeaker() },
                    )
                )
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                val startMs = System.currentTimeMillis()
                talkStartMs = startMs
                val number = phoneNumber ?: resolveLastCallNumber() ?: "Unknown"
                val (display, canonical) = resolveContact(number)
                val direction = if (seenRinging) CallDirection.INCOMING else CallDirection.OUTGOING
                Phs3DebugLog.onPoll("Call", "OFFHOOK direction=$direction number=$canonical display=$display")
                manager.register(
                    ActiveCallPhs3Handler(
                        callInfo = ActiveCallInfo(
                            displayName = display,
                            phoneNumber = canonical,
                            direction = direction,
                            connectionState = CallConnectionState.ACTIVE,
                            talkStartMs = startMs,
                        ),
                        onEndCall = { endCall() },
                        onMute    = { toggleMute() },
                        onSpeaker = { toggleSpeaker() },
                    )
                )
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Phs3DebugLog.onPoll("Call", "IDLE — unregistering ActiveCall")
                seenRinging = false
                talkStartMs = null
                manager.unregister("ActiveCall")
                // A call just ended — re-check for missed calls immediately,
                // since the log entry may not have been written yet. A short
                // delay gives the telephony stack time to commit the log row.
                kotlinx.coroutines.delay(1_500)
                refreshMissedCalls()
            }
        }
    }

    // ── Missed calls ──────────────────────────────────────────────────────────

    private suspend fun refreshMissedCalls() {
        val missed = queryMissedCalls()
        Phs3DebugLog.onPoll("Call", "missedCalls=${missed.size}")
        if (missed.isEmpty()) {
            manager.unregister("MissedCall")
        } else {
            manager.register(
                MissedCallPhs3Handler(
                    missedCalls = missed,
                    onCallBack  = { number -> dialNumber(number) },
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
     * Resolves a cached contact name for [rawNumber] from the call log.
     * Returns Pair(displayName or null, canonicalNumber).
     * Falls back to (null, rawNumber) if READ_CALL_LOG is not granted.
     */
    private suspend fun resolveContact(rawNumber: String): Pair<String?, String> =
        withContext(Dispatchers.IO) {
            try {
                val name = context.contentResolver.query(
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
                Pair(name, rawNumber)
            } catch (_: SecurityException) { Pair(null, rawNumber) }
            catch (_: Exception)          { Pair(null, rawNumber) }
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

    private fun toggleMute() {
        // Mute requires an active InCallService binding; open the system UI
        // as a fallback so the user can toggle mute there.
        openInCallUi()
    }

    private fun toggleSpeaker() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE)
                as android.media.AudioManager
        audio.isSpeakerphoneOn = !audio.isSpeakerphoneOn
    }

    private fun dialNumber(number: String) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_CALL,
            android.net.Uri.parse("tel:$number"),
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