package com.cmft.scaleai.ai

import com.cmft.scaleai.data.entity.ChatMessage
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prompt 模板构建器（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 职责：
 * 1. 生成「报告」与「对话」两种 prompt 文本；
 * 2. 做上下文裁剪，防止超出 DeepSeek 上下文窗口。
 */
object PromptBuilder {

    /** 上下文裁剪上限：报告类只保留最近 N 份历史报告 */
    const val MAX_REPORT_CONTEXT = 2

    /** 上下文裁剪上限：对话类只保留最近 N 条消息 */
    const val MAX_CHAT_CONTEXT = 20

    /** 系统人设（固定）：专业健身教练 + 营养师 */
    val systemPrompt: String = """
        你是「Scale AI 教练」，一位专业的健身教练兼注册营养师。
        请全程使用中文回答，语气鼓励、务实，不夸大、不虚构数据。
        你擅长解读体成分数据，并围绕增肌/减脂目标给出饮食与训练建议。
        所有建议必须基于用户提供的测量数据；数据缺失时如实说明，并给出一般性建议。
    """.trimIndent()

    /**
     * 构建「身体报告」Prompt。
     *
     * @param user     用户档案
     * @param current  本次测量
     * @param previous 上次测量（可空）
     * @param recent   近期趋势摘要（降序）
     * @param pastReports 历史报告消息（内部裁剪到最近 [MAX_REPORT_CONTEXT] 份）
     */
    fun buildReportPrompt(
        user: UserProfile,
        current: Measurement,
        previous: Measurement?,
        recent: List<Measurement>,
        pastReports: List<ChatMessage>
    ): String {
        val builder = StringBuilder()
        builder.append("你是一位专业的健身教练兼营养师。请根据以下用户数据，用中文生成一份结构化的身体状况分析与建议报告。\n\n")

        builder.append("【用户档案】\n").append(renderUser(user)).append('\n')
        builder.append("\n【本次测量】\n").append(renderMeasurement(current)).append('\n')
        builder.append("\n【上次测量】\n").append(renderMeasurement(previous)).append('\n')
        builder.append("\n【近期趋势摘要（最近 ${recent.size} 条）】\n").append(renderTrend(recent)).append('\n')

        builder.append("\n【历史报告（已裁剪，仅保留最近 ${MAX_REPORT_CONTEXT} 份）】\n")
        if (pastReports.isEmpty()) {
            builder.append("（暂无历史报告）\n")
        } else {
            pastReports.forEach { builder.append(it.content).append("\n---\n") }
        }

        builder.append("\n【任务】请严格按以下 5 段输出：\n")
        builder.append("1. 本次数据解读：逐项解读本次体成分数据（体重/体脂率/肌肉率/水分率/BMI/基础代谢等）。\n")
        builder.append("2. 与上次对比 + 趋势：对比本次与上次数据，结合近期趋势说明变化与走势。\n")
        builder.append("3. 饮食建议：结合目标体重与目标体脂率，给出具体可执行的饮食建议。\n")
        builder.append("4. 训练建议：结合「推 Push / 拉 Pull / 腿 Legs」PPL 计划，围绕增肌目标给出训练安排。\n")
        builder.append("5. 目标进度：对照用户目标，评估当前完成度并给出下一步建议。\n\n")
        builder.append("用中文、鼓励务实口吻回答，不要夸张，不要臆造数据。")
        return builder.toString()
    }

    /**
     * 构建「多轮对话」Prompt。
     * 内部只保留最近 [MAX_CHAT_CONTEXT] 条消息（超长上下文裁剪）。
     *
     * @param messages 按时间升序（旧→新）的最近消息
     */
    fun buildChatPrompt(messages: List<ChatMessage>, latestMeasurement: Measurement? = null): String {
        val trimmed = messages.takeLast(MAX_CHAT_CONTEXT)
        val builder = StringBuilder()
        builder.append("你是专业的健身教练兼营养师，请用中文以鼓励、务实的态度回答。")
        builder.append("\n以下是最近的对话记录：")
        if (trimmed.isEmpty()) {
            builder.append("（暂无历史对话）")
        } else {
            trimmed.forEach { msg ->
                val roleLabel = if (msg.role == "user") "用户" else "教练"
                builder.append("\n").append(roleLabel).append("：").append(msg.content)
            }
        }
        if (latestMeasurement != null) {
            builder.append("\n\n【当前最新身体数据】\n").append(renderMeasurement(latestMeasurement))
        }
        builder.append("\n请结合以上对话继续回答。")
        return builder.toString()
    }

    // ===== 渲染辅助 =====

    private fun renderUser(user: UserProfile): String {
        val gender = if (user.gender == "male") "男" else "女"
        val target = buildString {
            user.targetWeightKg?.let { append("目标体重 ${fmt(it)}kg；") }
            user.targetBodyFatPct?.let { append("目标体脂率 ${fmt(it)}%；") }
        }.trim()
        return buildString {
            append("姓名：${user.name}；性别：$gender；身高：${user.heightCm}cm；年龄：${user.age}岁")
            if (target.isNotEmpty()) append("\n目标：$target") else append("\n目标：未设置")
        }
    }

    private fun renderMeasurement(m: Measurement?): String {
        if (m == null) return "（无数据）"
        return buildString {
            append("时间：${formatTime(m.timestamp)}")
            append("\n体重：${fmt(m.weightKg)}kg")
            m.bodyFatPct?.let { append("\n体脂率：${fmt(it)}%") }
            m.muscleRatePct?.let { append("\n肌肉率：${fmt(it)}%") }
            m.waterPct?.let { append("\n水分率：${fmt(it)}%") }
            m.bonePct?.let { append("\n骨率：${fmt(it)}%") }
            m.proteinPct?.let { append("\n蛋白质率：${fmt(it)}%") }
            m.visceralFat?.let { append("\n内脏脂肪：${fmt(it)}") }
            m.bmrKcal?.let { append("\n基础代谢：${it} kcal") }
            m.bmi?.let { append("\nBMI：${fmt(it)}") }
        }
    }

    private fun renderTrend(recent: List<Measurement>): String {
        if (recent.isEmpty()) return "（暂无）"
        // 逆转为时间升序（旧→新）更符合趋势阅读习惯
        return recent.reversed().joinToString("\n") { renderMeasurement(it) }
    }

    private fun fmt(value: Double): String {
        val r = Math.round(value * 10) / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
