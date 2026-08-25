package com.cmft.scaleai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI 请求结果统一封装。
 * - [Success]：成功，返回模型文本
 * - [Error]：失败，返回用户可读的友好提示 + 可选错误码
 */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String, val code: Int? = null) : AiResult()
}

// ===== DeepSeek REST 请求/响应 DTO =====

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val stream: Boolean = false
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val message: ChatMessageDto? = null
)

@Serializable
data class ApiError(
    val error: ApiErrorDetail? = null
)

@Serializable
data class ApiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/**
 * DeepSeek Chat API 客户端。
 *
 * - 端点：https://api.deepseek.com/chat/completions
 * - 模型：deepseek-chat
 * - 超时：connectTimeout 15s / readTimeout 60s（长报告可能 30s+，默认 10s 会超时）
 * - API Key 通过 [apiKeyProvider] 惰性获取（来自 SettingsManager 的 DataStore）
 *
 * 所有异常都被捕获并映射为用户可读的友好提示（不抛裸异常）。
 */
class DeepSeekClient(
    private val apiKeyProvider: suspend () -> String?
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // 长报告可能 30s+，默认 10s 会超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 发送对话请求。
     * @param messages 完整消息序列（应包含 system 消息 + 若干 user/assistant 消息）
     */
    suspend fun chat(messages: List<ChatMessageDto>): AiResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext AiResult.Error("尚未配置 API Key，请先到「档案与设置」填写。", code = 401)
        }

        try {
            val requestBody = ChatRequest(model = "deepseek-chat", messages = messages)
            val request = Request.Builder()
                .url(API_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val parsed = json.decodeFromString<ChatResponse>(body)
                    val text = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    if (text.isEmpty()) {
                        AiResult.Error("模型未返回有效内容，请重试。", code = code)
                    } else {
                        AiResult.Success(text)
                    }
                } else {
                    AiResult.Error(mapError(code, body), code = code)
                }
            }
        } catch (e: IOException) {
            AiResult.Error("网络请求失败，请检查网络连接后重试。")
        } catch (e: Exception) {
            AiResult.Error("请求出错：${e.message ?: "未知错误"}")
        }
    }

    companion object {
        private const val API_URL = "https://api.deepseek.com/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 把 HTTP 状态码映射为用户可读的友好提示 */
        private fun mapError(code: Int, body: String): String = when (code) {
            401 -> "API Key 无效，请到「档案与设置」检查或重新填写。"
            402 -> "DeepSeek 余额不足，请充值后重试。"
            403 -> "无访问权限，请检查 API Key 是否有效。"
            429 -> "请求过于频繁，触发限流，请稍后重试。"
            in 500..599 -> "服务端繁忙（$code），请稍后重试。"
            else -> {
                val detail = runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromString<ApiError>(body).error?.message
                }.getOrNull()
                "请求失败（$code），${detail ?: "请稍后重试。"}"
            }
        }
    }
}
