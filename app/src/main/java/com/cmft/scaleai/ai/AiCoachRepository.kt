package com.cmft.scaleai.ai

import com.cmft.scaleai.data.ScaleRepository
import com.cmft.scaleai.data.SettingsManager
import com.cmft.scaleai.data.entity.ChatMessage
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.first

/**
 * AI 教练仓库：报告生成 + 多轮对话。
 *
 * 依赖：
 * - [ScaleRepository]：测量/对话/档案数据
 * - [SettingsManager]：读取 DeepSeek API Key
 * - [DeepSeekClient]：实际调用 DeepSeek Chat API
 *
 * 数据隐私说明：体成分数据在此处被拼进 prompt 发送至 DeepSeek 服务；
 * API Key 只保存在本地 DataStore，不直接暴露给调用方。
 */
class AiCoachRepository(
    private val repository: ScaleRepository,
    private val settingsManager: SettingsManager,
    injectedClient: DeepSeekClient? = null   // 可注入，便于单测
) {
    // API Key 来自 SettingsManager（DataStore）；默认自行构建客户端
    private val client: DeepSeekClient =
        injectedClient ?: DeepSeekClient { settingsManager.apiKey.first() }

    /**
     * 生成某次测量的 AI 报告。
     * 成功后将 assistant 回复存入 chat_messages，并把该测量标记为 reportGenerated = true。
     */
    suspend fun generateReport(userId: Long, measurementId: Long): AiResult {
        val measurement = repository.getMeasurement(measurementId)
            ?: return AiResult.Error("未找到对应的测量记录。")
        val user = repository.getUser(userId)
            ?: return AiResult.Error("未找到用户档案。")

        val messages = buildReportMessages(user, measurement)
        val result = client.chat(messages)

        if (result is AiResult.Success) {
            repository.insertChatMessage(
                ChatMessage(
                    userId = userId,
                    role = "assistant",
                    content = result.text,
                    timestamp = System.currentTimeMillis()
                )
            )
            repository.setReportGenerated(measurementId, true)
        }
        return result
    }

    /**
     * 报告失败后的重试入口（复用 [generateReport] 逻辑）。
     */
    suspend fun regenerateReport(userId: Long, measurementId: Long): AiResult =
        generateReport(userId, measurementId)

    /**
     * 多轮对话：先存用户消息，拉最近上下文并调用 API，成功后将 assistant 回复存档。
     */
    suspend fun chat(userId: Long, message: String): AiResult {
        if (message.isBlank()) return AiResult.Error("消息不能为空。")
        val user = repository.getUser(userId)
            ?: return AiResult.Error("未找到用户档案。")

        // 先记录用户消息
        repository.insertChatMessage(
            ChatMessage(
                userId = userId,
                role = "user",
                content = message,
                timestamp = System.currentTimeMillis()
            )
        )

        val chatMessages = buildChatMessages(userId)
        val result = client.chat(chatMessages)

        if (result is AiResult.Success) {
            repository.insertChatMessage(
                ChatMessage(
                    userId = userId,
                    role = "assistant",
                    content = result.text,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    // ===== 内部：构建请求消息序列 =====

    private suspend fun buildReportMessages(user: UserProfile, measurement: Measurement): List<ChatMessageDto> {
        // 最近测量（含本次，降序）
        val recent = repository.getRecentMeasurements(user.id, 8)
        // 上一次测量：本次之前最近的一条
        val previous = recent.firstOrNull { it.timestamp < measurement.timestamp }
        // 历史报告：只取最近 MAX_REPORT_CONTEXT 份（裁剪上下文）
        val reports = repository.getRecentChatMessages(user.id, 30)
            .filter { it.role == "assistant" }
            .take(PromptBuilder.MAX_REPORT_CONTEXT)

        val prompt = PromptBuilder.buildReportPrompt(user, measurement, previous, recent, reports)
        return listOf(
            ChatMessageDto(role = "system", content = PromptBuilder.systemPrompt),
            ChatMessageDto(role = "user", content = prompt)
        )
    }

    private suspend fun buildChatMessages(userId: Long): List<ChatMessageDto> {
        // 最近 MAX_CHAT_CONTEXT 条消息，逆转为时间升序（旧→新）
        val recent = repository.getRecentChatMessages(userId, PromptBuilder.MAX_CHAT_CONTEXT).asReversed()
        // 注入最新测量（体重/体脂等），让教练回答基于当前身体状况
        val latestMeasurement = repository.getLatestMeasurement(userId)
        val prompt = PromptBuilder.buildChatPrompt(recent, latestMeasurement)
        return listOf(
            ChatMessageDto(role = "system", content = PromptBuilder.systemPrompt),
            ChatMessageDto(role = "user", content = prompt)
        )
    }
}
