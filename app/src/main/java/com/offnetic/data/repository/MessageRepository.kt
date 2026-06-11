package com.offnetic.data.repository

import com.offnetic.domain.model.Message
import com.offnetic.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun insert(message: Message): Result<Long>
    suspend fun insertAll(messages: List<Message>): Result<Unit>
    suspend fun update(message: Message): Result<Unit>
    fun getMessagesForChat(chatId: String, limit: Int, offset: Int): Flow<Result<List<Message>>>
    fun getMessagesBefore(chatId: String, beforeId: Long, limit: Int): Flow<Result<List<Message>>>
    suspend fun getById(id: Long): Result<Message?>
    suspend fun getUnreadCount(chatId: String, myPublicKey: String): Result<Int>
    suspend fun markAsRead(chatId: String, myPublicKey: String): Result<Unit>
    suspend fun deleteAllForChat(chatId: String): Result<Unit>
    suspend fun getLatestBySession(sessionId: String): Result<Message?>
    suspend fun getLatestTimestamp(chatId: String): Result<Long?>
}