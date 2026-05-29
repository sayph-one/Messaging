package com.simplemobiletools.smsmessenger.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sayph.android.commons.SayphNotificationGuard
import com.sayph.android.commons.SayphStateChecker
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.smsmessenger.extensions.getNameFromAddress
import com.simplemobiletools.smsmessenger.extensions.notificationHelper
import com.simplemobiletools.smsmessenger.receivers.SmsReplayAlarmReceiver

private const val TAG = "SmsDowntimeReplay"
private const val REQUEST_CODE_END = 100
private const val REQUEST_CODE_POLL = 101
private const val POLL_INTERVAL_MS = 60 * 1000L // 1 minute

/**
 * Schedule two AlarmManager alarms when the first notification is suppressed during downtime:
 *
 * 1. **End-time alarm** — fires at the scheduled downtime end. Uses `setExactAndAllowWhileIdle`
 *    for precision even during Doze.
 * 2. **Polling alarm** — fires every ~1 minute to detect early termination by a parent. Uses
 *    `setAndAllowWhileIdle` (may be throttled to ~9 min during deep Doze).
 *
 * Idempotent: if the polling alarm's PendingIntent already exists, both alarms are already
 * scheduled and this function no-ops.
 */
fun scheduleDowntimeReplayAlarms(context: Context) {
    val appContext = context.applicationContext
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

    // Check if already scheduled (poll alarm as the sentinel)
    val existingPoll = PendingIntent.getBroadcast(
        appContext, REQUEST_CODE_POLL,
        Intent(appContext, SmsReplayAlarmReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
    if (existingPoll != null) return

    val downtimeEndMillis = SayphStateChecker.getState(appContext).downtimeEndMillis
    if (downtimeEndMillis <= 0L) return

    // End-time alarm — precise wakeup at the scheduled downtime end
    val endIntent = PendingIntent.getBroadcast(
        appContext, REQUEST_CODE_END,
        Intent(appContext, SmsReplayAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, downtimeEndMillis, endIntent)

    // Polling alarm — periodic check for early termination by a parent
    val pollIntent = PendingIntent.getBroadcast(
        appContext, REQUEST_CODE_POLL,
        Intent(appContext, SmsReplayAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis() + POLL_INTERVAL_MS,
        pollIntent,
    )

    Log.d(TAG, "Scheduled replay alarms: end=$downtimeEndMillis, poll in ${POLL_INTERVAL_MS}ms")
}

/**
 * Re-schedule both alarms for the next cycle. Called by [SmsReplayAlarmReceiver] when the poll
 * alarm fires but downtime is still active (handles extensions and ongoing downtime).
 */
fun rescheduleDowntimeReplayAlarms(context: Context) {
    val appContext = context.applicationContext
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val downtimeEndMillis = SayphStateChecker.getState(appContext).downtimeEndMillis

    // Update end-time alarm in case downtime was extended
    if (downtimeEndMillis > 0L) {
        val endIntent = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE_END,
            Intent(appContext, SmsReplayAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, downtimeEndMillis, endIntent)
    }

    // Schedule next poll
    val pollIntent = PendingIntent.getBroadcast(
        appContext, REQUEST_CODE_POLL,
        Intent(appContext, SmsReplayAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis() + POLL_INTERVAL_MS,
        pollIntent,
    )
}

/** Cancel both replay alarms. Called after a successful replay. */
fun cancelDowntimeReplayAlarms(context: Context) {
    val appContext = context.applicationContext
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

    listOf(REQUEST_CODE_END, REQUEST_CODE_POLL).forEach { code ->
        val pi = PendingIntent.getBroadcast(
            appContext, code,
            Intent(appContext, SmsReplayAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }
    Log.d(TAG, "Cancelled replay alarms")
}

/**
 * Re-post notifications for SMS messages that arrived during downtime. Synchronous — runs all
 * work on the calling thread. Clears the suppression timestamp and cancels alarms after replay.
 */
fun replaySuppressedSmsNotifications(context: Context) {
    val appContext = context.applicationContext
    val suppressionStart = SayphNotificationGuard.downtimeStartMillis(appContext)
    if (suppressionStart == 0L) {
        Log.d(TAG, "No suppressed notifications to replay")
        cancelDowntimeReplayAlarms(appContext)
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
    cancelDowntimeReplayAlarms(appContext)
}
