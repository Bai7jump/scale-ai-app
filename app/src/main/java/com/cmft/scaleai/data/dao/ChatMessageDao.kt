package com.cmft.scaleai.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cmft.scaleai.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp ASC")
    fun observeAll(userId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE userId = :userId ORDER BY timestamp DESC LIMIT :count")
    suspend fun getRecent(userId: Long, count: Int): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    suspend fun clearAll(userId: Long)
}
