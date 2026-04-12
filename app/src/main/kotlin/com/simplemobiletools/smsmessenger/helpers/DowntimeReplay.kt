package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.sayph.android.commons.SayphNotificationGuard
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.showReceivedMessageNotification

private const val TAG = "SmsDowntimeReplay"

/**
 * Re-post notifications for SMS messages that arrived while downtime was active and were
 * suppressed by the gate in [showReceivedMessageNotification]. Queries the system SMS provider
 * for unread inbox messages with `date > suppressionStart` and re-invokes the notification
 * function. The gate now lets these through because downtime has ended.
 *
 * Safe to call multiple times: the stored suppression timestamp is cleared by
 * [com.sayph.android.commons.SayphNotificationReleaseDispatcher] after in-process listeners
 * run, and clears on a second call here for manifest-receiver cold starts.
 *
 * Known limitation: this only replays SMS, not MMS. MMS replay would require reconstructing the
 * MMS body and attachment from a separate query; the reported bug is SMS-focused so we punt on
 * that for now.
 */
fun replaySuppressedSmsNotifications(context: Context) {
    val suppressionStart = SayphNotificationGuard.downtimeStartMillis(context)
    if (suppressionStart == 0L) {
        Log.d(TAG, "No suppressed notifications to replay")
        return
    }

    ensureBackgroundThread {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.DATE,
        )
        val selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            suppressionStart.toString(),
        )
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        var replayed = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLongValue(Telephony.Sms._ID)
                    val address = cursor.getStringValue(Telephony.Sms.ADDRESS) ?: continue
                    val body = cursor.getStringValue(Telephony.Sms.BODY) ?: ""
                    val threadId = cursor.getLongValue(Telephony.Sms.THREAD_ID)
                    context.showReceivedMessageNotification(id, address, body, threadId, null)
                    replayed++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query suppressed SMS messages for replay", e)
        }

        Log.d(TAG, "Replayed $replayed suppressed SMS notifications")
        SayphNotificationGuard.clearDowntimeStartMillis(context)
    }
}
