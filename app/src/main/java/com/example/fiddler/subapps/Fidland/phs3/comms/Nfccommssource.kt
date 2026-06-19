package com.example.fiddler.subapps.Fidland.phs3.comms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Comms source — NFC.
 *
 * On/off only, as agreed — NFC has no persistent "connected device" state
 * outside of a momentary tap, so there's nothing richer worth tracking.
 *
 * ── API split ─────────────────────────────────────────────────────────────────
 * API 33+ (Tiramisu): [NfcAdapter.ACTION_ADAPTER_STATE_CHANGED] gives us a
 * real broadcast, so we register a receiver and never poll.
 *
 * Below API 33: no such broadcast exists. We fall back to polling
 * [NfcAdapter.isEnabled] every [POLL_MS] — cheap, since it's just a Binder
 * call against NfcService, not a goes-to-disk or goes-to-radio query.
 */
class NfcCommsSource(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(context) }

    private val _info = MutableStateFlow(NfcCommsInfo(isEnabled = false))
    val info: Flow<NfcCommsInfo> = _info.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var pollJob: Job? = null
    private val POLL_MS = 3_000L

    fun start() {
        if (adapter == null) {
            // No NFC hardware on this device — stay permanently disabled,
            // nothing to register or poll.
            _info.value = NfcCommsInfo(isEnabled = false)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                        publish()
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            publish() // prime initial state
        } else {
            pollJob = scope.launch {
                while (true) {
                    publish()
                    delay(POLL_MS)
                }
            }
        }
    }

    fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) { /* already unregistered */ }
        }
        receiver = null

        pollJob?.cancel()
        pollJob = null

        _info.value = NfcCommsInfo(isEnabled = false)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun publish() {
        _info.value = NfcCommsInfo(isEnabled = adapter?.isEnabled == true)
    }
}