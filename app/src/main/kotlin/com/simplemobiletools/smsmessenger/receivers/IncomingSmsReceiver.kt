package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

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

                    Log.d(TAG, "Received SMS from $address")

                    // Note: Message logging is handled by SmsReceiver after whitelist filtering
                    // This receiver is only for early interception and logging
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process incoming SMS", e)
            }
        }
    }
}
