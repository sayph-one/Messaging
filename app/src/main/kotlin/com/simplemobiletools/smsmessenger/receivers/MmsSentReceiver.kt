package com.simplemobiletools.smsmessenger.receivers

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Telephony
import android.widget.Toast
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.deleteMessage
import com.simplemobiletools.smsmessenger.helpers.MessageSyncHelper
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import java.io.File

/** Handles updating databases and states when a MMS message is sent. */
class MmsSentReceiver : SendStatusReceiver() {

    override fun updateAndroidDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        val uri = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI))
        val originalResentMessageId = intent.getLongExtra(EXTRA_ORIGINAL_RESENT_MESSAGE_ID, -1L)
        val messageBox = if (receiverResultCode == Activity.RESULT_OK) {
            Telephony.Mms.MESSAGE_BOX_SENT
        } else {
            val msg = context.getString(R.string.unknown_error_occurred_sending_message, receiverResultCode)
            context.toast(msg = msg, length = Toast.LENGTH_LONG)
            Telephony.Mms.MESSAGE_BOX_FAILED
        }

        val values = ContentValues(1).apply {
            put(Telephony.Mms.MESSAGE_BOX, messageBox)
        }

        try {
            context.contentResolver.update(uri, values, null, null)
        } catch (e: SQLiteException) {
            context.showErrorToast(e)
        }

        // In case of resent message, delete original to prevent duplication
        if (originalResentMessageId != -1L) {
            context.deleteMessage(originalResentMessageId, true)
        }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null) {
            File(filePath).delete()
        }
    }

    override fun updateAppDatabase(context: Context, intent: Intent, receiverResultCode: Int) {
        refreshMessages()

        // Log ALL MMS messages (both successful and failed) to pending sync
        val uri = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI))
        val messageId = uri.lastPathSegment?.toLongOrNull()

        if (messageId != null) {
            ensureBackgroundThread {
                try {
                    // Query MMS database for timestamp
                    val mmsCursor = context.contentResolver.query(
                        uri,
                        arrayOf("date"),
                        null, null, null
                    )

                    var timestamp = System.currentTimeMillis()
                    mmsCursor?.use {
                        if (it.moveToFirst()) {
                            val dateIndex = it.getColumnIndex("date")
                            if (dateIndex >= 0) {
                                timestamp = it.getLong(dateIndex) * 1000L // MMS date is in seconds
                            }
                        }
                    }

                    // Query for recipient address
                    val addrCursor = context.contentResolver.query(
                        Uri.parse("content://mms/$messageId/addr"),
                        arrayOf("address", "type"),
                        null, null, null
                    )

                    var address = ""
                    addrCursor?.use {
                        while (it.moveToNext()) {
                            val typeIndex = it.getColumnIndex("type")
                            val addressIndex = it.getColumnIndex("address")

                            if (typeIndex >= 0 && addressIndex >= 0) {
                                val addrType = it.getInt(typeIndex)
                                // 151 = PduHeaders.TO, 137 = PduHeaders.FROM
                                if (addrType == 151) {
                                    address = it.getString(addressIndex) ?: ""
                                    if (address.isNotEmpty()) break
                                }
                            }
                        }
                    }

                    // Get MMS text body from parts
                    val partsCursor = context.contentResolver.query(
                        Uri.parse("content://mms/part"),
                        arrayOf("mid", "ct", "text"),
                        "mid = ?",
                        arrayOf(messageId.toString()),
                        null
                    )

                    var body = ""
                    partsCursor?.use {
                        while (it.moveToNext()) {
                            val ctIndex = it.getColumnIndex("ct")
                            val textIndex = it.getColumnIndex("text")

                            if (ctIndex >= 0 && textIndex >= 0) {
                                val contentType = it.getString(ctIndex)
                                if (contentType == "text/plain") {
                                    body = it.getString(textIndex) ?: ""
                                    if (body.isNotEmpty()) break
                                }
                            }
                        }
                    }

                    if (body.isEmpty()) {
                        body = "[MMS]" // Fallback for MMS without text
                    }

                    val msgStatus = if (receiverResultCode == Activity.RESULT_OK) "sent" else "failed"
                    val finalErrorCode = if (receiverResultCode != Activity.RESULT_OK) receiverResultCode else null

                    MessageSyncHelper.logMessage(
                        context = context,
                        address = address,
                        body = body,
                        direction = "outbound",
                        msgType = "mms",
                        timestamp = timestamp,
                        msgStatus = msgStatus,
                        errorCode = finalErrorCode,
                        seenByUser = 1,
                        systemMessageId = messageId
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MmsSentReceiver", "Failed to log MMS to pending sync", e)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CONTENT_URI = "content_uri"
        private const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_ORIGINAL_RESENT_MESSAGE_ID = "original_message_id"
    }
}
