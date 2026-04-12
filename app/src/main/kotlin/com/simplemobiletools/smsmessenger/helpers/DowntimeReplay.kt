package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.sayph.android.commons.SayphNotificationGuard
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.smsmessenger.extensions.getNameFromAddress
import com.simplemobiletools.smsmessenger.extensions.notificationHelper

private const val TAG = "SmsDowntimeReplay"

/**
 * Re-post notifications for SMS messages that arrived while downtime was active and were
 * suppressed by the gate in `showReceivedMessageNotification`. Queries the system SMS provider
 * for unread inbox messages with `date > suppressionStart` and posts notifications directly
 * via [NotificationHelper].
 *
 * This function is **synchronous** — it does all work on the calling thread. This is critical
 * for the [com.simplemobiletools.smsmessenger.receivers.DowntimeEndReceiver] path where using
 * `ensureBackgroundThread` would let `onReceive` return before the work completes, allowing the
 * system to kill the process.
 *
 * Known limitation: this only replays SMS, not MMS.
 */
fun replaySuppressedSmsNotifications(context: Context) {
    val appContext = context.applicationContext
    val suppressionStart = SayphNotificationGuard.downtimeStartMillis(appContext)
    if (suppressionStart == 0L) {
        Log.d(TAG, "No suppressed notifications to replay")
        return
    }

    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.THREAD_ID,
    )
    val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
        suppressionStart.toString(),
    )
    val sortOrder = "${Telephony.Sms.DATE} ASC"

    var replayed = 0
    try {
        appContext.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getStringValue(Telephony.Sms.ADDRESS) ?: continue
                val body = cursor.getStringValue(Telephony.Sms.BODY) ?: ""
                val threadId = cursor.getLongValue(Telephony.Sms.THREAD_ID)
                val messageId = cursor.getLongValue(Telephony.Sms._ID)
                val senderName = appContext.getNameFromAddress(address, null)
                appContext.notificationHelper.showMessageNotification(
                    messageId, address, body, threadId, null, senderName
                )
                replayed++
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to query suppressed SMS messages for replay", e)
    }

    Log.d(TAG, "Replayed $replayed suppressed SMS notifications")
    SayphNotificationGuard.clearDowntimeStartMillis(appContext)
}
