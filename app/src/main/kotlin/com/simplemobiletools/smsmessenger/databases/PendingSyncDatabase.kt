package com.simplemobiletools.smsmessenger.databases

import android.content.Context
import androidx.room.*

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
    val timestamp: Long = System.currentTimeMillis()
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
}

@Database(entities = [PendingMessage::class], version = 1, exportSchema = true)
abstract class PendingSyncDatabase : RoomDatabase() {
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        @Volatile
        private var INSTANCE: PendingSyncDatabase? = null

        fun getInstance(context: Context): PendingSyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PendingSyncDatabase::class.java,
                    "pending_sync_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
