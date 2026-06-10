package com.simplemobiletools.smsmessenger.databases

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "pending_messages")
data class PendingMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "time_utc")
    val timeUtc: String,

    @ColumnInfo(name = "msg_with")
    val msgWith: String,

    @ColumnInfo(name = "msg_details")
    val msgDetails: String,

    @ColumnInfo(name = "msg_type")
    val msgType: String, // "sms" or "mms"

    @ColumnInfo(name = "msg_direction")
    val msgDirection: String, // "inbound" or "outbound"

    @ColumnInfo(name = "synced")
    val synced: Boolean = false,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    // New fields for message status tracking
    @ColumnInfo(name = "msg_status")
    val msgStatus: String? = null, // "sent", "failed", "received", "blocked"

    @ColumnInfo(name = "error_code")
    val errorCode: Int? = null, // Android error code for failed messages

    @ColumnInfo(name = "seen_by_user")
    val seenByUser: Int? = null, // 0 or 1 (boolean)

    @ColumnInfo(name = "system_message_id")
    val systemMessageId: Long? = null // ID from Android SMS database for correlation
)

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedMessages(): List<PendingMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessage): Long

    @Query("UPDATE pending_messages SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM pending_messages WHERE synced = 1 AND timestamp < :cutoffTime")
    suspend fun cleanupOldSyncedMessages(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM pending_messages WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("DELETE FROM pending_messages")
    suspend fun deleteAllPending()
}

@Database(entities = [PendingMessage::class], version = 2, exportSchema = true)
abstract class PendingSyncDatabase : RoomDatabase() {
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        @Volatile
        private var INSTANCE: PendingSyncDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns for message status tracking
                database.execSQL("ALTER TABLE pending_messages ADD COLUMN msg_status TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE pending_messages ADD COLUMN error_code INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE pending_messages ADD COLUMN seen_by_user INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE pending_messages ADD COLUMN system_message_id INTEGER DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): PendingSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PendingSyncDatabase::class.java,
                    "pending_sync_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
