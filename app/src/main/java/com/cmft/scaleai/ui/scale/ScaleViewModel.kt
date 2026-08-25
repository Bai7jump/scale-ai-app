package com.cmft.scaleai.ui.scale

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cmft.scaleai.ScaleAiApplication
import com.cmft.scaleai.ai.AiReportService
import com.cmft.scaleai.ble.ScaleConnection
import com.cmft.scaleai.ble.ScaleReading
import com.cmft.scaleai.ble.ScaleSessionManager
import com.cmft.scaleai.ble.ScaleSessionState
import com.cmft.scaleai.calc.BodyCompositionCalculator
import com.cmft.scaleai.calc.UserMatch
import com.cmft.scaleai.calc.UserMatcher
import com.cmft.scaleai.data.SettingsManager
import com.cmft.scaleai.data.entity.ChatMessage
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 称重页 ViewModel
 *
 * 状态机驱动的称重全流程（Stage 5）：
 *  Idle → Scanning → Connecting → Receiving → Confirming → Result / Timeout
 *
 * 关键流程（硬性要求，必须按此顺序）：
 *  BLE会话完成(0x02包) → UserMatcher匹配建议用户 → [弹窗确认人选] → 按确认人选计算体成分 → 保存Measurement → 触发AI报告(异步,失败可重试)
 *
 * 为什么先确认再计算：体成分公式按性别(gender)计算，先入库再改选会导致数据错误且不可恢复。
 * 所以必须：拿到测量原始值(weightKg+impedance) → match() 得出建议用户 → 等用户弹窗确认 →
 * 用确认用户的 gender/height/age 调 BodyCompositionCalculator.calculate() 算体成分 → 存 Measurement。
 *
 * 手动输入兜底：body 输入体重 + 选人 → 存 Measurement(source="manual", 体成分字段全 null) → 复用确认弹窗。
 */
class ScaleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as ScaleAiApplication).repository
    private val settingsManager = SettingsManager(application)

    // 运行时可空：无蓝牙服务的设备为 null，不做初始化崩溃
    private val bluetoothAdapter: BluetoothAdapter? =
        application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothAdapter
    private val connection = ScaleConnection()
    private val sessionManager: ScaleSessionManager by lazy {
        ScaleSessionManager(requireNotNull(bluetoothAdapter), connection)
    }

    private val _uiState = MutableStateFlow(ScaleUiState())
    val uiState: StateFlow<ScaleUiState> = _uiState

    // 待确认状态：确认前暂存原始测量值，绝不提前入库
    private var pendingReading: ScaleReading? = null
    private var pendingManualWeight: Double? = null
    private var pendingSource: String? = null
    private var pendingUsers: List<UserProfile> = emptyList()

    // 最近一次报告的缓存（重试用）
    private var lastReportUserId: Long? = null
    private var lastReportMeasurement: Measurement? = null
    private var lastReportMeasurementId: Long? = null
    private var lastReportHasBody: Boolean = false

    init {
        // 映射底层 BLE 会话状态 → 页面阶段（扫描/连接/接收）
        if (bluetoothAdapter != null) {
            viewModelScope.launch {
                sessionManager.state.collect { s ->
                    when (s) {
                        ScaleSessionState.Scanning -> updatePhase(ScalePhase.Scanning, "正在扫描体脂秤…")
                        ScaleSessionState.Connecting -> updatePhase(ScalePhase.Connecting, "正在连接体脂秤…")
                        ScaleSessionState.Receiving -> updatePhase(ScalePhase.Receiving, "正在接收测量数据…")
                        else -> Unit
                    }
                }
            }
        }
    }

    // ===================== BLE 同步 =====================

    /**
     * 开始 BLE 称重会话：扫描 → 连接 → 收数据 → 完成包后进入确认。
     */
    fun startBleSession() {
        if (bluetoothAdapter == null) {
            _uiState.update { it.copy(message = "当前设备不支持蓝牙") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ScalePhase.Scanning, statusText = "准备扫描…", message = null, reportStatus = ReportStatus.None) }
            val users = repository.getAllUsers()
            if (users.isEmpty()) {
                _uiState.update { it.copy(phase = ScalePhase.Idle, message = "请先在设置中创建用户档案") }
                return@launch
            }
            // 阻塞直到完成/超时
            val reading = sessionManager.startSession()
            if (reading == null) {
                _uiState.update { it.copy(phase = ScalePhase.Timeout, message = "称重会话超时，未获取到数据") }
                return@launch
            }
            prepareConfirmation(
                weightKg = reading.weightKg,
                impedance = reading.impedance,
                users = users,
                source = "ble",
                reading = reading,
                manualWeight = null
            )
        }
    }

    // ===================== 手动输入兜底 =====================

    /**
     * 手动输入体重：校验后复用确认流程（体成分不计算），选人后保存 source="manual"。
     */
    fun startManualEntry(weightText: String) {
        viewModelScope.launch {
            val weight = weightText.trim().toDoubleOrNull()
            if (weight == null || weight <= 0) {
                _uiState.update { it.copy(message = "请输入合法的体重数值") }
                return@launch
            }
            val users = repository.getAllUsers()
            if (users.isEmpty()) {
                _uiState.update { it.copy(message = "请先在设置中创建用户档案") }
                return@launch
            }
            prepareConfirmation(
                weightKg = weight,
                impedance = null,
                users = users,
                source = "manual",
                reading = null,
                manualWeight = weight
            )
        }
    }

    // ===================== 确认 → 计算 → 保存 =====================

    /**
     * 用户确认人选（弹窗「本次是谁？」）后，才用该用户的性别/身高/年龄计算体成分并入库。
     */
    fun confirmSelection(user: UserProfile) {
        viewModelScope.launch {
            val reading = pendingReading
            val manualWeight = pendingManualWeight
            val source = pendingSource ?: "ble"
            val weight = reading?.weightKg ?: manualWeight ?: return@launch
            val impedance = if (source == "ble") reading?.impedance else null

            // 关键：先确认人选，再用其档案计算体成分，避免先入库后改选导致数据错误
            repository.setActiveUser(user.id)

            val comp = if (source == "ble" && impedance != null) {
                BodyCompositionCalculator.calculate(weight, impedance, user.heightCm, user.age, user.gender)
            } else null

            val m = Measurement(
                userId = user.id,
                timestamp = System.currentTimeMillis(),
                weightKg = weight,
                impedance = impedance,
                bodyFatPct = comp?.bodyFatPct,
                waterPct = comp?.waterPct,
                muscleRatePct = comp?.muscleRatePct,
                bonePct = comp?.bonePct,
                proteinPct = comp?.proteinPct,
                visceralFat = comp?.visceralFat,
                bmrKcal = comp?.bmrKcal,
                bmi = comp?.bmi,
                source = source,
                reportGenerated = false
            )
            val id = repository.insertMeasurement(m)
            val saved = m.copy(id = id)

            // 异步触发 AI 报告（失败可重试）
            triggerReport(user.id, saved, id, comp != null)

            _uiState.update {
                it.copy(
                    phase = ScalePhase.Result,
                    message = null,
                    lastSavedMeasurement = saved,
                    match = null,
                    users = emptyList(),
                    selectedUserId = null,
                    lowConfidence = false,
                    reading = null
                )
            }
            clearPending()
        }
    }

    fun reset() {
        _uiState.update { ScaleUiState() }
        clearPending()
    }

    // ===================== AI 报告（异步，可重试） =====================

    /**
     * 触发 AI 报告生成。异步，失败标记为 Failed 并缓存以支持重试。
     * @param hasBody 是否含体成分数据（manual 为 false → Prompt 注明无体脂数据）
     */
    fun triggerReport(userId: Long, measurement: Measurement, measurementId: Long, hasBody: Boolean) {
        lastReportUserId = userId
        lastReportMeasurement = measurement
        lastReportMeasurementId = measurementId
        lastReportHasBody = hasBody
        viewModelScope.launch {
            _uiState.update { it.copy(reportStatus = ReportStatus.Generating) }
            try {
                val apiKey = settingsManager.apiKey.first()
                val user = repository.getUser(userId) ?: throw IllegalStateException("用户不存在")
                val content = AiReportService.generateReport(apiKey, user, measurement)
                repository.insertChatMessage(
                    ChatMessage(
                        userId = userId,
                        role = "assistant",
                        content = content,
                        timestamp = System.currentTimeMillis()
                    )
                )
                repository.setReportGenerated(measurementId, true)
                _uiState.update { it.copy(reportStatus = ReportStatus.Success, message = "AI 报告已生成") }
            } catch (e: Exception) {
                _uiState.update { it.copy(reportStatus = ReportStatus.Failed, message = "AI 报告生成失败：${e.message}") }
            }
        }
    }

    /**
     * 重试最近一次失败的 AI 报告。
     */
    fun retryReport() {
        val uid = lastReportUserId ?: return
        val mid = lastReportMeasurementId ?: return
        val m = lastReportMeasurement ?: return
        triggerReport(uid, m, mid, lastReportHasBody)
    }

    // ===================== 内部辅助 =====================

    /**
     * 准备确认状态：匹配建议用户 → 进入 Confirming 阶段（弹窗）。
     * 此处不计算体成分、不入库。
     */
    private suspend fun prepareConfirmation(
        weightKg: Double,
        impedance: Double?,
        users: List<UserProfile>,
        source: String,
        reading: ScaleReading?,
        manualWeight: Double?
    ) {
        val activeUser = repository.getActiveUser() ?: users.firstOrNull()
        // 预计算各用户最近5次平均体重（UserMatcher.getAvgWeight 是非挂起 lambda，需提前取好）
        val avgWeights = users.associate { u ->
            val recent = repository.getRecentMeasurements(u.id, 5)
            u.id to (if (recent.isEmpty()) null else recent.map { it.weightKg }.average())
        }
        val match = try {
            UserMatcher.match(weightKg, impedance, users, activeUser) { id -> avgWeights[id] }
        } catch (e: Exception) {
            _uiState.update { it.copy(phase = ScalePhase.Idle, message = "匹配失败：${e.message}") }
            return
        }
        pendingReading = reading
        pendingManualWeight = manualWeight
        pendingSource = source
        pendingUsers = users
        _uiState.update {
            it.copy(
                phase = ScalePhase.Confirming,
                statusText = "等待确认人选",
                match = match,
                users = users,
                reading = reading,
                selectedUserId = match.user.id,
                lowConfidence = !match.confidence,
                measurementWeightKg = weightKg,
                message = null
            )
        }
    }

    private fun clearPending() {
        pendingReading = null
        pendingManualWeight = null
        pendingSource = null
        pendingUsers = emptyList()
    }

    private fun updatePhase(phase: ScalePhase, status: String) {
        _uiState.update {
            // 仅在测量进行中阶段更新，避免覆盖 Confirming/Result/Timeout
            if (it.phase == ScalePhase.Idle || it.phase == ScalePhase.Scanning ||
                it.phase == ScalePhase.Connecting || it.phase == ScalePhase.Receiving
            ) {
                it.copy(phase = phase, statusText = status)
            } else it
        }
    }
}

/**
 * 称重页状态机阶段
 */
enum class ScalePhase { Idle, Scanning, Connecting, Receiving, Confirming, Result, Timeout }

/**
 * AI 报告生成状态
 */
enum class ReportStatus { None, Generating, Success, Failed }

/**
 * 称重页 UI 状态
 */
data class ScaleUiState(
    val phase: ScalePhase = ScalePhase.Idle,
    val statusText: String = "",
    val reading: ScaleReading? = null,
    val match: UserMatch? = null,
    val users: List<UserProfile> = emptyList(),
    val selectedUserId: Long? = null,
    val lowConfidence: Boolean = false,
    val reportStatus: ReportStatus = ReportStatus.None,
    val message: String? = null,
    val lastSavedMeasurement: Measurement? = null,
    val measurementWeightKg: Double = 0.0
)
