package com.simplemobiletools.smsmessenger.receivers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.MessageSyncHelper
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

/** Handles updating databases and states when a SMS message is sent. */
class SmsStatusSentReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val messageUri: Uri? = intent.data
        val resultCode = resultCode
        val messagingUtils = context.messagingUtils

        val type = if (resultCode == Activity.RESULT_OK) {
            Sms.MESSAGE_TYPE_SENT
        } else {
            Sms.MESSAGE_TYPE_FAILED
        }
        messagingUtils.updateSmsMessageSendingStatus(messageUri, type)
        messagingUtils.maybeShowErrorToast(
            resultCode = resultCode,
            errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE)
        )
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val messageUri = intent.data
        if (messageUri != null) {
            val messageId = messageUri.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val type = if (receiverResultCode == Activity.RESULT_OK) {
                    Sms.MESSAGE_TYPE_SENT
                } else {
                    showSendingFailedNotification(context, messageId)
                    Sms.MESSAGE_TYPE_FAILED
                }

                context.messagesDB.updateType(messageId, type)
                refreshMessages()

                // Log ALL SMS messages (both successful and failed) to pending sync
                try {
                    val cursor = context.contentResolver.query(
                        android.provider.Telephony.Sms.CONTENT_URI,
                        arrayOf("address", "body", "date"),
                        "_id = ?",
                        arrayOf(messageId.toString()),
                        null
                    )

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val addressIndex = it.getColumnIndex("address")
                            val bodyIndex = it.getColumnIndex("body")
                            val dateIndex = it.getColumnIndex("date")

                            if (addressIndex >= 0 && bodyIndex >= 0 && dateIndex >= 0) {
                                val address = it.getString(addressIndex)
                                val body = it.getString(bodyIndex) ?: ""
                                val timestamp = it.getLong(dateIndex)
                                val errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, NO_ERROR_CODE)
                                val finalErrorCode = if (errorCode != NO_ERROR_CODE) errorCode else null

                                val msgStatus = if (type == Sms.MESSAGE_TYPE_SENT) "sent" else "failed"

                                MessageSyncHelper.logMessage(
                                    context = context,
                                    address = address,
                                    body = body,
                                    direction = "outbound",
                                    msgType = "sms",
                                    timestamp = timestamp,
                                    msgStatus = msgStatus,
                                    errorCode = finalErrorCode,
                                    seenByUser = 1, // User sent it, so they've seen it
                                    systemMessageId = messageId
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SmsStatusSentReceiver", "Failed to log SMS to pending sync", e)
                }
            }
        }
    }

    private fun showSendingFailedNotification(context: Context, messageId: Long) {
        Handler(Looper.getMainLooper()).post {
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return@post
            }
            val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            ensureBackgroundThread {
                val address = context.getMessageRecipientAddress(messageId)
                val threadId = context.getThreadId(address)
                val recipientName = context.getNameFromAddress(address, privateCursor)
                context.notificationHelper.showSendingFailedNotification(recipientName, threadId)
            }
        }
    }
}
