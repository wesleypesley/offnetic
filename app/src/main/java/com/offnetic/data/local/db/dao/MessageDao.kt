package com.offnetic.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offnetic.data.local.db.entity.Message
import kotlinx.coroutines.flow.Flow

// NOTE (DB14): several queries below embed MessageDeliveryState names as SQL string
// literals ('SAVED', 'SENT_LOCAL', ...). Renaming an enum constant silently breaks
// them — update these queries together with the enum.
@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>)

    @Update
    suspend fun update(message: Message)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    fun getMessagesForChat(chatId: String, limit: Int, offset: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND id < :beforeId ORDER BY timestamp ASC LIMIT :limit")
    fun getMessagesBefore(chatId: String, beforeId: Long, limit: Int): Flow<List<Message>>

    // Newest :limit messages, returned in ascending order for display. The plain
    // ASC LIMIT query returns the OLDEST rows — in chats past the limit, new
    // messages would never appear (D4).
    @Query("SELECT * FROM (SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC, id DESC LIMIT :limit) ORDER BY timestamp ASC, id ASC")
    fun getRecentMessagesForChat(chatId: String, limit: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): Message?

    @Query("SELECT * FROM messages WHERE messageUuid = :messageUuid LIMIT 1")
    suspend fun getByMessageUuid(messageUuid: String): Message?

    @Query("UPDATE messages SET deliveryState = 'DELIVERED' WHERE messageUuid = :messageUuid AND deliveryState IN ('SENT_LOCAL','SENT_RELAY')")
    suspend fun markDelivered(messageUuid: String)

    @Query("UPDATE messages SET deliveryState = 'READ' WHERE messageUuid = :messageUuid AND deliveryState IN ('SENT_LOCAL','SENT_RELAY','DELIVERED')")
    suspend fun markRead(messageUuid: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0 AND senderPublicKey != :myPublicKey")
    suspend fun getUnreadCount(chatId: String, myPublicKey: String): Int

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND senderPublicKey != :myPublicKey")
    suspend fun markAsRead(chatId: String, myPublicKey: String)

    @Query("SELECT messageUuid FROM messages WHERE chatId = :chatId AND isRead = 0 AND senderPublicKey != :myPublicKey")
    suspend fun getUnreadIncomingUuids(chatId: String, myPublicKey: String): List<String>

    @Query("UPDATE messages SET isRead = 1 WHERE messageUuid IN (:uuids)")
    suspend fun markUuidsAsRead(uuids: List<String>)

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND deliveryState = 'SAVED' AND senderPublicKey = :myPublicKey ORDER BY timestamp ASC")
    suspend fun getUnsentMessagesForChat(chatId: String, myPublicKey: String): List<Message>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBySession(sessionId: String): Message?

    @Query("SELECT MAX(timestamp) FROM messages WHERE chatId = :chatId")
    suspend fun getLatestTimestamp(chatId: String): Long?

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT chatId, MAX(id) AS maxId FROM messages GROUP BY chatId
        ) grouped ON m.id = grouped.maxId
        ORDER BY m.timestamp DESC
    """)
    fun getChatSummaries(): Flow<List<Message>>

    @Query("SELECT chatId, COUNT(*) AS count FROM messages WHERE isRead = 0 AND senderPublicKey != :myPublicKey GROUP BY chatId")
    fun getUnreadCountsPerChat(myPublicKey: String): Flow<List<UnreadCountRow>>

    @Query("SELECT * FROM messages WHERE deliveryState = 'SAVED' AND senderPublicKey = :myPublicKey AND type IN (1, 2, 3, 4) ORDER BY timestamp ASC")
    suspend fun getUnsentAttachments(myPublicKey: String): List<Message>

    @Query("UPDATE messages SET deliveryState = 'SENT_RELAY' WHERE messageUuid = :messageUuid AND deliveryState = 'SAVED'")
    suspend fun markSentRelay(messageUuid: String)

    @Query("UPDATE messages SET deliveryState = :state WHERE messageUuid = :messageUuid")
    suspend fun setDeliveryState(messageUuid: String, state: String)
}

data class UnreadCountRow(
    val chatId: String,
    val count: Int
)