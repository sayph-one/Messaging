package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.smsmessenger.helpers.replaySuppressedSmsNotifications

/**
 * Manifest-registered receiver that wakes the Messaging process on a downtime-end broadcast
 * and replays any SMS notifications that were suppressed while downtime was active.
 *
 * This exists alongside the in-process listener registered from [com.simplemobiletools.smsmessenger.App.onCreate]
 * so that replay still happens if the app was force-stopped or killed during downtime.
 */
class DowntimeEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.sayph.DOWNTIME_STATE_CHANGED") return
        val isInDowntime = intent.getBooleanExtra("is_in_downtime", false)
        if (isInDowntime) return
        replaySuppressedSmsNotifications(context)
    }
}
