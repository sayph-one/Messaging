package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sayph.android.commons.SayphNotificationGuard
import com.simplemobiletools.smsmessenger.helpers.replaySuppressedSmsNotifications
import com.simplemobiletools.smsmessenger.helpers.rescheduleDowntimeReplayAlarms

/**
 * Alarm-triggered receiver for replaying suppressed SMS notifications after downtime ends.
 *
 * Fired by two AlarmManager alarms:
 * - **End-time alarm** — fires at the scheduled downtime end time.
 * - **Poll alarm** — fires every ~1 minute to detect early termination by a parent.
 *
 * Both alarms target this single receiver. On each fire:
 * - If downtime is still active: re-schedule for the next cycle (handles extensions).
 * - If downtime has ended: run the synchronous replay, then cancel all alarms.
 */
class SmsReplayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val appContext = context.applicationContext
                if (SayphNotificationGuard.shouldSuppress(appContext)) {
                    // Still in downtime (extended, or poll fired before end). Re-schedule.
                    rescheduleDowntimeReplayAlarms(appContext)
                } else {
                    replaySuppressedSmsNotifications(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
