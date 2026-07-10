package com.offnetic.data.repository

import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.domain.model.Message
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    override suspend fun insert(message: Message): Result<Long> {
        return try { Result.Success(messageDao.insert(message.toEntity())) }
        catch (e: Exception) { Result.Error("Failed to insert message", e) }
    }

    override suspend fun insertAll(messages: List<Message>): Result<Unit> {
        return try { messageDao.insertAll(messages.map { it.toEntity() }); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to insert messages", e) }
    }

    override suspend fun update(message: Message): Result<Unit> {
        return try { messageDao.update(message.toEntity()); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to update message", e) }
    }

    override fun getMessagesForChat(chatId: String, limit: Int, offset: Int): Flow<Result<List<Message>>> {
        return messageDao.getMessagesForChat(chatId, limit, offset)
            .map<List<com.offnetic.data.local.db.entity.Message>, Result<List<Message>>> { entities ->
                Result.Success(entities.map { Message.fromEntity(it) })
            }
            .catch { emit(Result.Error("Failed to observe messages", it)) }
    }

    override fun getMessagesBefore(chatId: String, beforeId: Long, limit: Int): Flow<Result<List<Message>>> {
        return messageDao.getMessagesBefore(chatId, beforeId, limit)
            .map<List<com.offnetic.data.local.db.entity.Message>, Result<List<Message>>> { entities ->
                Result.Success(entities.map { Message.fromEntity(it) })
            }
            .catch { emit(Result.Error("Failed to observe older messages", it)) }
    }

    override suspend fun getById(id: Long): Result<Message?> {
        return try {
            val entity = messageDao.getById(id)
            Result.Success(entity?.let { Message.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get message by id", e) }
    }

    override suspend fun getUnreadCount(chatId: String, myPublicKey: String): Result<Int> {
        return try { Result.Success(messageDao.getUnreadCount(chatId, myPublicKey)) }
        catch (e: Exception) { Result.Error("Failed to get unread count", e) }
    }

    override suspend fun markAsRead(chatId: String, myPublicKey: String): Result<Unit> {
        return try { messageDao.markAsRead(chatId, myPublicKey); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to mark as read", e) }
    }

    override suspend fun deleteAllForChat(chatId: String): Result<Unit> {
        return try { messageDao.deleteAllForChat(chatId); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to delete messages for chat", e) }
    }

    override suspend fun deleteById(id: Long): Result<Unit> {
        return try { messageDao.deleteById(id); Result.Success(Unit) }
        catch (e: Exception) { Result.Error("Failed to delete message", e) }
    }

    override suspend fun getLatestBySession(sessionId: String): Result<Message?> {
        return try {
            val entity = messageDao.getLatestBySession(sessionId)
            Result.Success(entity?.let { Message.fromEntity(it) })
        } catch (e: Exception) { Result.Error("Failed to get latest message by session", e) }
    }

    override suspend fun getLatestTimestamp(chatId: String): Result<Long?> {
        return try { Result.Success(messageDao.getLatestTimestamp(chatId)) }
        catch (e: Exception) { Result.Error("Failed to get latest timestamp", e) }
    }
}