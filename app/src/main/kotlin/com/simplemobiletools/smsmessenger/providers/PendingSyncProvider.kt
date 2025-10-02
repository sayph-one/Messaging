package com.simplemobiletools.smsmessenger.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.simplemobiletools.smsmessenger.BuildConfig
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

    override fun onCreate(): Boolean {
        database = PendingSyncDatabase.getInstance(context!!)
        Log.d(TAG, "PendingSyncProvider initialized")
        return true
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

                    // Convert to cursor
                    val cursor = MatrixCursor(
                        arrayOf("id", "time_utc", "msg_with", "msg_details", "msg_type", "msg_direction", "timestamp")
                    )

                    messages.forEach { message ->
                        cursor.addRow(arrayOf(
                            message.id,
                            message.timeUtc,
                            message.msgWith,
                            message.msgDetails,
                            message.msgType,
                            message.msgDirection,
                            message.timestamp
                        ))
                        Log.d(TAG, "Added message to cursor: id=${message.id}")
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
