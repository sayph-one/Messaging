package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.simplemobiletools.smsmessenger.helpers.MessageSyncHelper

class IncomingSmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "IncomingSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                messages.forEach { smsMessage ->
                    val address = smsMessage.displayOriginatingAddress ?: ""
                    val body = smsMessage.messageBody ?: ""
                    val timestamp = smsMessage.timestampMillis

                    Log.d(TAG, "Received SMS from $address")

                    // Log the message for sync
                    MessageSyncHelper.logMessage(
                        context = context,
                        address = address,
                        body = body,
                        direction = "inbound",
                        timestamp = timestamp
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process incoming SMS", e)
            }
        }
    }
}
