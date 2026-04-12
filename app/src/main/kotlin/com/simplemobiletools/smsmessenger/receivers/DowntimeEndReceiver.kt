package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.smsmessenger.helpers.replaySuppressedSmsNotifications

/**
 * Manifest-registered receiver that wakes the Messaging process on a downtime-end broadcast
 * and replays any SMS notifications that were suppressed while downtime was active.
 *
 * Uses [goAsync] to keep the process alive while the synchronous replay function queries
 * the SMS provider and posts notifications. Without this, Android would kill the process
 * as soon as [onReceive] returns, before the replay work completes.
 */
class DowntimeEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.sayph.DOWNTIME_STATE_CHANGED") return
        if (intent.getBooleanExtra("is_in_downtime", false)) return

        val pendingResult = goAsync()
        Thread {
            try {
                replaySuppressedSmsNotifications(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
