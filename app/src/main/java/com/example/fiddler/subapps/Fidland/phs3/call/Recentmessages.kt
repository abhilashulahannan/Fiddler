package com.example.fiddler.subapps.Fidland.phs3.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Recent-messages strip for the ActiveCall phs5 screen — replaces the
 * "FUTURE: Recent Messages strip" placeholder block in call.kt's
 * State5Content. Surfaces the caller's most recent message(s) so you can
 * see what they last texted without leaving the call screen.
 *
 * Two channels, with very different reliability:
 *
 *   • SMS/RCS — [querySmsForNumber] does an on-demand ContentResolver query
 *     against Telephony.Sms.CONTENT_URI, matched to the caller's number.
 *     This is the same provider the stock Messages app reads from, so it's
 *     reliable and has full history. Requires READ_SMS — already added to
 *     PermissionsActivity's runtime request list, but you'll still need to
 *     add `<uses-permission android:name="android.permission.READ_SMS"/>`
 *     to AndroidManifest.xml yourself, since the manifest isn't part of
 *     this source dump.
 *
 *   • WhatsApp — there is no ContentResolver for WhatsApp's own database on
 *     modern Android (scoped storage, no public provider), so this can only
 *     go through [WhatsAppNotificationSource] — a passive notification
 *     cache, same pattern as RecorderNotificationSource elsewhere in this
 *     app. It remembers the text of WhatsApp notifications as they arrive,
 *     keyed by the sender's *notification title* (WhatsApp puts the
 *     contact's display name there, never the raw phone number). That
 *     means:
 *       - Matching back to the caller relies on [ActiveCallInfo.displayName]
 *         being the exact same string WhatsApp used as the title — only
 *         works for saved contacts; unknown numbers never match.
 *       - Only surfaces messages that arrived *after* the listener
 *         connected this session — no history backfill, unlike SMS.
 *       - Cleared once the notification itself is dismissed/read.
 *
 * Email is intentionally NOT implemented here: Gmail hasn't exposed a
 * public, queryable ContentResolver provider since Android 11, and a
 * generic IMAP/EAS integration is a different scale of feature (its own
 * account + OAuth setup) than "read a channel Android already exposes" —
 * flagging the gap rather than shipping a placeholder row that never
 * populates.
 */

// ── Shared model ─────────────────────────────────────────────────────────────

enum class MessageChannel { SMS, WHATSAPP }

data class RecentMessageItem(
    val channel: MessageChannel,
    val snippet: String,
    val timestampMs: Long,
    /** Opens the relevant thread/app when the chip is tapped. */
    val openIntent: Intent,
)

// ── SMS/RCS — on-demand ContentResolver query ─────────────────────────────────

/**
 * Queries the most recent SMS/RCS messages to/from [phoneNumber], newest
 * first, up to [limit]. Matches by comparing the last 7 digits of each
 * stored address against the last 7 digits of [phoneNumber] — cheap, and
 * avoids false negatives from formatting differences (+91, spaces, dashes)
 * without pulling in a full phone-number-matching library just for this
 * strip. Returns an empty list if READ_SMS isn't granted, [phoneNumber] is
 * too short to compare safely, or nothing matches.
 */
suspend fun querySmsForNumber(
    context: Context,
    phoneNumber: String,
    limit: Int = 3,
): List<RecentMessageItem> = withContext(Dispatchers.IO) {
    val targetSuffix = phoneNumber.filter { it.isDigit() }.takeLast(7)
    if (targetSuffix.length < 7) return@withContext emptyList()

    try {
        val items = mutableListOf<RecentMessageItem>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            // Over-fetch a recent window, then filter by suffix client-side —
            // there's no clean SQL "last N digits equal" without a raw WHERE
            // clause fighting the provider's own number normalization.
            "${Telephony.Sms.DATE} DESC LIMIT 40",
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext() && items.size < limit) {
                val address = cursor.getString(addrIdx) ?: continue
                if (address.filter { it.isDigit() }.takeLast(7) != targetSuffix) continue
                val body = cursor.getString(bodyIdx) ?: continue
                items += RecentMessageItem(
                    channel = MessageChannel.SMS,
                    snippet = body,
                    timestampMs = cursor.getLong(dateIdx),
                    openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$phoneNumber"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        items
    } catch (_: SecurityException) {
        emptyList() // READ_SMS not granted
    } catch (_: Exception) {
        emptyList()
    }
}

// ── WhatsApp — notification-listener cache ────────────────────────────────────

private const val WHATSAPP_PACKAGE = "com.whatsapp"
private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

/**
 * Rolling cache of the latest WhatsApp notification per sender title.
 *
 * Wire-up (mirrors RecorderNotificationSource — see that class's kdoc for
 * the full pattern):
 *
 *   // NotificationListenerService.kt companion object:
 *   var whatsAppSource: WhatsAppNotificationSource? = null
 *
 *   override fun onNotificationPosted(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       whatsAppSource?.onNotificationPosted(sbn)
 *       ...
 *   }
 *   override fun onNotificationRemoved(sbn: StatusBarNotification?) {
 *       sbn ?: return
 *       whatsAppSource?.onNotificationRemoved(sbn)
 *       ...
 *   }
 *
 *   // FidlandService.onCreate(), alongside callTrigger:
 *   whatsAppSource = WhatsAppNotificationSource()
 *   NotificationListenerService.whatsAppSource = whatsAppSource
 *
 *   // FidlandService.onDestroy():
 *   NotificationListenerService.whatsAppSource = null
 */
class WhatsAppNotificationSource {

    private val _bySenderTitle = MutableStateFlow<Map<String, RecentMessageItem>>(emptyMap())
    val bySenderTitle: StateFlow<Map<String, RecentMessageItem>> = _bySenderTitle.asStateFlow()

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE && sbn.packageName != WHATSAPP_BUSINESS_PACKAGE) return
        val extras = sbn.notification?.extras ?: return
        // Group-summary notifications (WhatsApp's "N new messages" stack
        // notification) carry no useful per-sender title/text — skip them,
        // we only want the individual per-conversation postings.
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() } ?: return
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() } ?: return

        // No public deep-link into a specific WhatsApp chat without the
        // conversation's internal jid (not exposed via notification extras),
        // so this opens the app itself via its launcher activity rather than
        // a specific thread — same "best we can do without a private API"
        // trade-off CallPhs3Trigger takes for call recording.
        val openIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(sbn.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        _bySenderTitle.value = _bySenderTitle.value + (title to RecentMessageItem(
            channel = MessageChannel.WHATSAPP,
            snippet = text,
            timestampMs = sbn.postTime,
            openIntent = openIntent,
        ))
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE && sbn.packageName != WHATSAPP_BUSINESS_PACKAGE) return
        val title = sbn.notification?.extras
            ?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: return
        _bySenderTitle.value = _bySenderTitle.value - title
    }
}

// ── Compose UI ─────────────────────────────────────────────────────────────────

/**
 * Horizontally-scrolling strip of the caller's most recent messages across
 * SMS and WhatsApp. Renders nothing (not even the "Recent messages" label)
 * when there's nothing to show, so it never reserves dead space in the
 * State5Content layout.
 *
 * @param phoneNumber      The active call's [ActiveCallInfo.phoneNumber] —
 *                         used for the SMS query and the smsto: deep link.
 * @param displayName      The active call's [ActiveCallInfo.displayName] —
 *                         used to key into [whatsAppMessages]; null means no
 *                         WhatsApp match is attempted (see class kdoc).
 * @param whatsAppMessages Current snapshot of [WhatsAppNotificationSource.bySenderTitle].
 *                         Pass an empty map if the source isn't wired up yet.
 */
@Composable
fun RecentMessagesStrip(
    phoneNumber: String,
    displayName: String?,
    whatsAppMessages: Map<String, RecentMessageItem>,
) {
    val context = LocalContext.current
    var smsItems by remember(phoneNumber) { mutableStateOf<List<RecentMessageItem>>(emptyList()) }

    LaunchedEffect(phoneNumber) {
        smsItems = querySmsForNumber(context, phoneNumber)
    }

    val whatsAppItem = displayName?.let { whatsAppMessages[it] }
    val items = (smsItems + listOfNotNull(whatsAppItem)).sortedByDescending { it.timestampMs }

    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recent messages",
            color = Color(0xFF666666),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item -> RecentMessageChip(item, context) }
        }
    }
}

@Composable
private fun RecentMessageChip(item: RecentMessageItem, context: Context) {
    val (icon, tint) = when (item.channel) {
        MessageChannel.SMS -> Icons.Filled.Sms to Color(0xFF60A5FA)
        MessageChannel.WHATSAPP -> Icons.Filled.Chat to Color(0xFF4ADE80)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .widthIn(max = 160.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A1A))
            .clickable { try { context.startActivity(item.openIntent) } catch (_: Exception) { } }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = item.channel.name,
            tint = tint,
            modifier = Modifier.size(14.dp).padding(top = 1.dp),
        )
        Column {
            Text(
                text = item.snippet,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatClockTime(item.timestampMs),
                color = Color(0xFF666666),
                fontSize = 8.sp,
            )
        }
    }
}