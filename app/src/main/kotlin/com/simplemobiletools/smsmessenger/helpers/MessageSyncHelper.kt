package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplemobiletools.smsmessenger.databases.PendingMessage
import com.simplemobiletools.smsmessenger.databases.PendingSyncDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object MessageSyncHelper {
    private const val TAG = "MessageSyncHelper"

    // Constants for broadcast
    const val ACTION_MESSAGE_LOGGED = "com.sayph.sayphagent.MESSAGE_LOGGED"
    const val EXTRA_MESSAGE_ID = "message_id"

    /**
     * Format date to ISO 8601 format compatible with API 23+
     * Returns format like: 2025-10-01T14:30:00+01:00
     */
    private fun formatDateToISO8601(timestamp: Long): String {
        val date = Date(timestamp)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getDefault()

        val formattedDate = sdf.format(date)

        // Manually add timezone offset
        val tz = TimeZone.getDefault()
        val offsetMillis = tz.getOffset(timestamp)
        val offsetHours = offsetMillis / (1000 * 60 * 60)
        val offsetMinutes = (offsetMillis / (1000 * 60)) % 60

        val sign = if (offsetMillis >= 0) "+" else "-"
        val tzOffset = String.format(
            Locale.US,
            "%s%02d:%02d",
            sign,
            Math.abs(offsetHours),
            Math.abs(offsetMinutes)
        )

        return "$formattedDate$tzOffset"
    }

    /**
     * Log a message to the pending sync database
     * This should be called whenever a message is sent or received
     */
    fun logMessage(
        context: Context,
        address: String,
        body: String,
        direction: String, // "inbound" or "outbound"
        timestamp: Long = System.currentTimeMillis()
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PendingSyncDatabase.getInstance(context)

                val pendingMessage = PendingMessage(
                    timeUtc = formatDateToISO8601(timestamp),
                    msgWith = address,
                    msgDetails = body,
                    msgType = "sms",
                    msgDirection = direction,
                    synced = false,
                    timestamp = timestamp
                )

                val messageId = db.pendingMessageDao().insert(pendingMessage)

                Log.d(TAG, "Logged message for sync: id=$messageId, direction=$direction, address=$address")

                // Notify agent app that a new message has been logged
                notifyAgentApp(context, messageId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to log message for sync", e)
            }
        }
    }

    /**
     * Notify the agent app that a message has been logged
     * This allows the agent to potentially sync immediately if needed
     */
    private fun notifyAgentApp(context: Context, messageId: Long) {
        try {
            val intent = Intent(ACTION_MESSAGE_LOGGED).apply {
                setPackage("com.sayph.sayphagent")
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Notified agent app of new message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify agent app", e)
        }
    }

    /**
     * Get count of unsynced messages
     */
    suspend fun getUnsyncedCount(context: Context): Int {
        return try {
            val db = PendingSyncDatabase.getInstance(context)
            db.pendingMessageDao().getUnsyncedCount()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get unsynced count", e)
            0
        }
    }
}
