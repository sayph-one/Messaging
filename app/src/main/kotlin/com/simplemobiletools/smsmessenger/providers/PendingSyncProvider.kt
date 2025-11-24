package com.simplemobiletools.smsmessenger.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.simplemobiletools.smsmessenger.BuildConfig
import com.simplemobiletools.smsmessenger.databases.MessagesDatabase
import com.simplemobiletools.smsmessenger.databases.PendingSyncDatabase
import kotlinx.coroutines.runBlocking

class PendingSyncProvider : ContentProvider() {

    companion object {
        private const val TAG = "PendingSyncProvider"
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.pendingsync"
        const val PATH_UNSYNCED = "unsynced"
        const val PATH_MARK_SYNCED = "mark_synced"

        private const val CODE_UNSYNCED = 1
        private const val CODE_MARK_SYNCED = 2

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_UNSYNCED")

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_UNSYNCED, CODE_UNSYNCED)
            addURI(AUTHORITY, "$PATH_MARK_SYNCED/*", CODE_MARK_SYNCED)
        }
    }

    private lateinit var database: PendingSyncDatabase
    private lateinit var messagesDatabase: MessagesDatabase

    override fun onCreate(): Boolean {
        database = PendingSyncDatabase.getInstance(context!!)
        messagesDatabase = MessagesDatabase.getInstance(context!!)
        Log.d(TAG, "PendingSyncProvider initialized")
        return true
    }

    /**
     * Determine the final status of a message by querying Android SMS database and app's local database
     */
    private fun determineMessageStatus(
        phoneNumber: String,
        timestamp: Long,
        direction: String,
        msgType: String,
        systemMessageId: Long?
    ): Triple<String, Int?, Int> {
        if (direction == "outbound") {
            return determineOutboundStatus(phoneNumber, timestamp, msgType, systemMessageId)
        } else {
            return determineInboundStatus(phoneNumber, timestamp, msgType, systemMessageId)
        }
    }

    /**
     * Determine status for outbound messages by checking Android SMS database
     */
    private fun determineOutboundStatus(
        phoneNumber: String,
        timestamp: Long,
        msgType: String,
        systemMessageId: Long?
    ): Triple<String, Int?, Int> {
        try {
            val uri = if (msgType.lowercase() == "mms") {
                Uri.parse("content://mms")
            } else {
                Uri.parse("content://sms")
            }

            // Query for sent or failed messages
            val selection = if (systemMessageId != null) {
                "_id = ?"
            } else {
                "address = ? AND date = ? AND type IN (2, 5)" // 2=SENT, 5=FAILED
            }

            val selectionArgs = if (systemMessageId != null) {
                arrayOf(systemMessageId.toString())
            } else {
                arrayOf(phoneNumber, timestamp.toString())
            }

            val cursor = context?.contentResolver?.query(
                uri,
                arrayOf("_id", "type", "status", "error_code"),
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val typeIndex = it.getColumnIndex("type")
                    val errorCodeIndex = it.getColumnIndex("error_code")

                    if (typeIndex >= 0) {
                        val type = it.getInt(typeIndex)

                        when (type) {
                            5 -> { // MESSAGE_TYPE_FAILED
                                val errorCode = if (errorCodeIndex >= 0) it.getInt(errorCodeIndex) else null
                                return Triple("failed", errorCode, 1)
                            }
                            2 -> { // MESSAGE_TYPE_SENT
                                return Triple("sent", null, 1)
                            }
                            4 -> { // MESSAGE_TYPE_OUTBOX (pending)
                                // Don't return pending messages - they should not be synced yet
                                Log.d(TAG, "Message is still pending, skipping")
                                return Triple("pending", null, 1)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining outbound status", e)
        }

        // Default to "sent" for outbound messages if we can't determine status
        // (user initiated it, so they've seen it)
        return Triple("sent", null, 1)
    }

    /**
     * Determine status for inbound messages by checking if they exist in app's conversation database
     */
    private fun determineInboundStatus(
        phoneNumber: String,
        timestamp: Long,
        msgType: String,
        systemMessageId: Long?
    ): Triple<String, Int?, Int> {
        Log.d(TAG, "determineInboundStatus: phone=$phoneNumber, timestamp=$timestamp, type=$msgType, systemId=$systemMessageId")
        try {
            // Check if message exists in Android SMS database
            val uri = if (msgType.lowercase() == "mms") {
                Uri.parse("content://mms/inbox")
            } else {
                Uri.parse("content://sms/inbox")
            }

            val selection = if (systemMessageId != null) {
                "_id = ?"
            } else {
                "address = ? AND date = ?"
            }

            val selectionArgs = if (systemMessageId != null) {
                arrayOf(systemMessageId.toString())
            } else {
                arrayOf(phoneNumber, timestamp.toString())
            }

            Log.d(TAG, "Querying system SMS inbox: uri=$uri, selection=$selection, args=${selectionArgs.joinToString()}")

            var existsInSystemDb = false
            var isRead = false
            var messageId: Long? = null

            val systemCursor = context?.contentResolver?.query(
                uri,
                arrayOf("_id", "read", "seen"),
                selection,
                selectionArgs,
                null
            )

            systemCursor?.use {
                if (it.moveToFirst()) {
                    existsInSystemDb = true
                    val readIndex = it.getColumnIndex("read")
                    val idIndex = it.getColumnIndex("_id")

                    if (readIndex >= 0) {
                        isRead = it.getInt(readIndex) == 1
                    }
                    if (idIndex >= 0) {
                        messageId = it.getLong(idIndex)
                    }
                    Log.d(TAG, "Found in system DB: id=$messageId, read=$isRead")
                } else {
                    Log.d(TAG, "NOT found in system SMS inbox")
                }
            }

            // Check if message exists in app's local conversation database
            if (existsInSystemDb && messageId != null) {
                val existsInConversationDb = runBlocking {
                    val result = messagesDatabase.MessagesDao().getMessageWithId(messageId!!)
                    Log.d(TAG, "Checking app conversation DB for messageId=$messageId: exists=${result != null}")
                    result != null
                }

                if (existsInConversationDb) {
                    // Message was delivered to the app
                    val seenByUser = if (isRead) 1 else 0
                    Log.d(TAG, "Message status: RECEIVED (in both DBs), seenByUser=$seenByUser")
                    return Triple("received", null, seenByUser)
                } else {
                    // Message exists in system DB but not in app DB = blocked
                    Log.d(TAG, "Message status: BLOCKED (in system DB but NOT in app DB)")
                    return Triple("blocked", null, 0)
                }
            } else if (!existsInSystemDb) {
                // Message was logged to pending_messages but NOT in system SMS DB
                // This means the app intercepted and blocked it before Android could store it
                Log.d(TAG, "Message status: BLOCKED (NOT in system DB - intercepted by app)")
                return Triple("blocked", null, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining inbound status", e)
        }

        // Default fallback (should rarely reach here)
        Log.d(TAG, "Falling back to default: received (unexpected path)")
        return Triple("received", null, 0)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "query() called with URI: $uri")
        return when (uriMatcher.match(uri)) {
            CODE_UNSYNCED -> {
                Log.d(TAG, "Query for unsynced messages")
                runBlocking {
                    val messages = database.pendingMessageDao().getUnsyncedMessages()
                    Log.d(TAG, "Found ${messages.size} unsynced messages in database")

                    // Convert to cursor with new columns
                    val cursor = MatrixCursor(
                        arrayOf(
                            "id",
                            "time_utc",
                            "msg_with",
                            "msg_details",
                            "msg_type",
                            "msg_direction",
                            "timestamp",
                            "msg_status",
                            "error_code",
                            "seen_by_user"
                        )
                    )

                    messages.forEach { message ->
                        // Determine status dynamically if not already set
                        val (msgStatus, errorCode, seenByUser) = if (message.msgStatus != null) {
                            // Use stored values if available
                            Triple(message.msgStatus, message.errorCode, message.seenByUser ?: 0)
                        } else {
                            // Determine status by querying system SMS database
                            determineMessageStatus(
                                message.msgWith,
                                message.timestamp,
                                message.msgDirection,
                                message.msgType,
                                message.systemMessageId
                            )
                        }

                        // Skip pending outbound messages
                        if (msgStatus == "pending") {
                            Log.d(TAG, "Skipping pending message: id=${message.id}")
                            return@forEach
                        }

                        cursor.addRow(arrayOf(
                            message.id,
                            message.timeUtc,
                            message.msgWith,
                            message.msgDetails,
                            message.msgType,
                            message.msgDirection,
                            message.timestamp,
                            msgStatus,
                            errorCode,
                            seenByUser
                        ))
                        Log.d(TAG, "Added message to cursor: id=${message.id}, status=$msgStatus, error=$errorCode, seen=$seenByUser")
                    }

                    Log.d(TAG, "Returning cursor with ${cursor.count} rows")
                    cursor
                }
            }
            else -> {
                Log.w(TAG, "Unknown URI: $uri, matcher result: ${uriMatcher.match(uri)}")
                null
            }
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return when (uriMatcher.match(uri)) {
            CODE_MARK_SYNCED -> {
                val idsString = uri.lastPathSegment
                val ids = idsString?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

                if (ids.isNotEmpty()) {
                    runBlocking {
                        database.pendingMessageDao().markAsSynced(ids)
                        Log.d(TAG, "Marked ${ids.size} messages as synced")

                        // Clean up old synced messages (older than 7 days)
                        val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                        database.pendingMessageDao().cleanupOldSyncedMessages(cutoffTime)
                    }
                    ids.size
                } else {
                    Log.w(TAG, "No valid IDs provided for marking as synced")
                    0
                }
            }
            else -> {
                Log.w(TAG, "Unknown URI for update: $uri")
                0
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.w(TAG, "Insert not supported")
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.w(TAG, "Delete not supported")
        return 0
    }

    override fun getType(uri: Uri): String? = null
}
