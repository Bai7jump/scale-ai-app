package com.cmft.scaleai.ui.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cmft.scaleai.ScaleAiApplication
import com.cmft.scaleai.ai.AiCoachRepository
import com.cmft.scaleai.ai.AiResult
import com.cmft.scaleai.data.SettingsManager
import com.cmft.scaleai.data.entity.ChatMessage
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AI 教练页 ViewModel
 *
 * 数据流设计（响应式）：
 * - [dataFlow]：组合「用户档案 + 当前激活档案」，再用 flatMapLatest 切换到
 *   该用户的「对话记录 + 测量记录」。（切用户时内部自动 re-subscribe，旧用户流被 cancel）
 * - [transient]：瞬时状态（loading / reportStatus / error），由用户动作（发送/重试/切档案）驱动。
 * - [uiState]：dataFlow 与 transient 合并后对外暴露，供 Compose 消费。
 *
 * 报告说明：AI 生成的报告内容以「assistant 消息」形式存于 chat_messages（与测量无外键关联），
 * 因此报告卡片取「当前用户最近一条 assistant 消息」作为报告正文。
 */
class AiCoachViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ScaleAiApplication).repository
    private val settingsManager = SettingsManager(application)
    private val aiRepo = AiCoachRepository(repository, settingsManager)

    // 用户切档案：非空时优先使用该 id 作为当前档案；切换时重新订阅该用户的对话/测量
    private val activeUserId = MutableStateFlow<Long?>(null)

    // 瞬时状态：AI 请求中 / 报告生成状态 / 最近错误
    private val transient = MutableStateFlow(CoachTransient())

    /**
     * 响应式数据流：
     * 用户列表 + 当前档案 → 切换订阅当前档案的「对话 + 测量」。
     */
    private val dataFlow = combine(
        repository.observeUsers(),
        activeUserId
    ) { users, forcedId ->
        val active = forcedId?.let { id -> users.firstOrNull { it.id == id } }
            ?: users.firstOrNull { it.isActive }
            ?: users.firstOrNull()
        active to users
    }.flatMapLatest { (active, users) ->
        if (active == null) {
            flowOf(CoachData(users = users))
        } else {
            combine(
                repository.observeChatMessages(active.id),
                repository.observeMeasurements(active.id)
            ) { chat, measurements ->
                CoachData(
                    users = users,
                    activeUser = active,
                    chatMessages = chat,
                    measurements = measurements,
                    latestMeasurement = measurements.firstOrNull() // DESC → 第一条即最新
                )
            }
        }
    }

    // 对外暴露的 UI 状态：响应式数据 + 瞬时状态合并
    val uiState: StateFlow<CoachUiState> = combine(dataFlow, transient) { data, t ->
        CoachUiState(
            users = data.users,
            activeUser = data.activeUser,
            chatMessages = data.chatMessages,
            latestMeasurement = data.latestMeasurement,
            reportStatus = t.reportStatus,
            loading = t.loading,
            error = t.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachUiState())

    // ===================== 用户操作 =====================

    /**
     * 切换当前档案：更新 activeUserId（触发 re-subscribe）+ 持久化 isActive。
     */
    fun switchUser(userId: Long) {
        if (activeUserId.value == userId) return
        activeUserId.value = userId
        transient.value = CoachTransient()
        viewModelScope.launch { repository.setActiveUser(userId) }
    }

    /**
     * 发送对话：先记录用户消息、调 AI，成功后 assistant 回复自动落库并被观察流推送。
     * loading 期间禁止重复发送。
     */
    fun sendMessage(text: String) {
        val userId = uiState.value.activeUser?.id ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            transient.update { it.copy(loading = true, error = null) }
            when (val result = aiRepo.chat(userId, text.trim())) {
                is AiResult.Success ->
                    transient.update { it.copy(loading = false) }
                is AiResult.Error ->
                    transient.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /**
     * 重试生成报告：仅当最新测量的 reportGenerated == false 时有效。
     * 成功后 [AiCoachRepository] 会把 assistant 回复落库并置 reportGenerated = true，
     * 观察流随即自动刷新报告卡片。
     */
    fun regenerateReport() {
        val user = uiState.value.activeUser ?: return
        val m = uiState.value.latestMeasurement ?: return
        if (m.reportGenerated) return
        viewModelScope.launch {
            transient.update {
                it.copy(loading = true, reportStatus = ReportStatus.Generating, error = null)
            }
            when (val result = aiRepo.regenerateReport(user.id, m.id)) {
                is AiResult.Success ->
                    transient.update {
                        it.copy(loading = false, reportStatus = ReportStatus.Success)
                    }
                is AiResult.Error ->
                    transient.update {
                        it.copy(loading = false, reportStatus = ReportStatus.Failed, error = result.message)
                    }
            }
        }
    }

    /** 清除底部横幅错误。 */
    fun clearError() = transient.update { it.copy(error = null) }
}

/**
 * AI 报告生成状态。
 */
enum class ReportStatus { None, Generating, Success, Failed }

/**
 * 响应式数据部分（用户/对话/测量）；瞬时状态单独存放，避免每次 Room 发射都重建。
 */
data class CoachData(
    val users: List<UserProfile> = emptyList(),
    val activeUser: UserProfile? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val latestMeasurement: Measurement? = null
)

/**
 * 瞬时状态。
 */
data class CoachTransient(
    val loading: Boolean = false,
    val reportStatus: ReportStatus = ReportStatus.None,
    val error: String? = null
)

/**
 * AI 教练页 UI 状态。
 */
data class CoachUiState(
    val users: List<UserProfile> = emptyList(),        // 当前可用档案
    val activeUser: UserProfile? = null,                // 当前激活档案
    val chatMessages: List<ChatMessage> = emptyList(),  // 当前档案的对话记录（时间升序）
    val latestMeasurement: Measurement? = null,         // 当前档案最新一次测量
    val reportStatus: ReportStatus = ReportStatus.None, // 报告生成状态
    val loading: Boolean = false,                       // AI 请求中（发送/重试）
    val error: String? = null                           // 最近一次错误提示
)
