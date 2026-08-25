package com.cmft.scaleai.ai

import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 报告生成服务
 *
 * 通过 DeepSeek Chat API 基于称重数据生成健康报告，纯 Kotlin + HttpURLConnection（无需额外依赖）。
 *
 * 关键设计：
 *  - [generateReport] 为挂起函数，在 IO 线程执行（异步，不阻塞测量保存流程）。
 *  - 未配置 API Key 或网络/解析失败时抛出异常 → 由上层标记失败 → 通过 [retryReport] 重试。
 *  - Prompt 会根据是否有体脂数据自动调整：手动输入（无体脂）会明确注明「暂无体脂成分数据」。
 */
object AiReportService {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    /**
     * 生成健康报告
     * @param apiKey DeepSeek API Key（可空；为空时抛异常，可重试）
     * @param user 确认后的用户档案（性别/身高/年龄用于报告上下文）
     * @param measurement 已保存的测量记录
     * @return 报告文本
     */
    suspend fun generateReport(
        apiKey: String?,
        user: UserProfile,
        measurement: Measurement
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException("未配置 DeepSeek API Key")
        }
        val prompt = buildPrompt(user, measurement)
        callDeepSeek(apiKey, prompt)
    }

    /**
     * 构建 Prompt：手动输入（无体脂数据）时明确注明，仅围绕体重给出建议。
     */
    fun buildPrompt(user: UserProfile, measurement: Measurement): String {
        val hasBody = measurement.bodyFatPct != null
        return buildString {
            append("请根据以下称重数据生成一份简短的中文健康报告（约150-250字），给出体重变化与健康建议：")
            append("\n姓名：${user.name}，性别：${if (user.gender == "male") "男" else "女"}，身高：${user.heightCm}cm，年龄：${user.age} 岁")
            append("\n体重：${"%.1f".format(measurement.weightKg)} kg")
            if (hasBody) {
                append("\n体脂率：${"%.1f".format(measurement.bodyFatPct!!)}%，BMI：${"%.1f".format(measurement.bmi ?: 0.0)}，基础代谢：${measurement.bmrKcal ?: 0} kcal，内脏脂肪：${"%.1f".format(measurement.visceralFat ?: 0.0)}")
            } else {
                append("\n备注：本次为手动输入体重，暂无体脂成分数据，请仅围绕体重给出建议。")
            }
            append("\n要求：通俗易懂、语气温和、可执行。")
        }
    }

    /**
     * 调用 DeepSeek Chat API（阻塞 IO 线程）
     */
    private fun callDeepSeek(apiKey: String, prompt: String): String {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")

            val payload = JSONObject()
                .put("model", MODEL)
                .put("temperature", 0.7)
                .put("max_tokens", 512)
                .put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt)
                ))
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw RuntimeException("DeepSeek API 返回 $code：$err")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parseContent(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 解析 DeepSeek 返回内容（choices[0].message.content）
     */
    private fun parseContent(body: String): String {
        val content = JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        if (content.isBlank()) throw RuntimeException("AI 返回空内容")
        return content
    }
}
