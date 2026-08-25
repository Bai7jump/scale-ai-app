package com.cmft.scaleai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 对话消息（报告 + 多轮对话）
 * role: "user" / "assistant"
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,               // 归属档案
    val role: String,               // "user" / "assistant"
    val content: String,            // 消息内容
    val timestamp: Long             // 时间戳(ms)
)
