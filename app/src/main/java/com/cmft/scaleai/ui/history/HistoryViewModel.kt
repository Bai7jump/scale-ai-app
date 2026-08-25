package com.cmft.scaleai.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 历史页 ViewModel：负责加载所有用户、当前选中用户及其测量记录。
 * - 支持顶部用户切换（你/她），默认选中当前激活档案。
 * - 用 flatMapLatest 观察：当 selectedUserId 变化时，切换监听对应用户的测量记录。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as com.cmft.scaleai.ScaleAiApplication).repository

    // 用户手动选择的档案 id（默认 null => 使用当前激活档案）
    private val selectedUserId = MutableStateFlow<Long?>(null)

    /**
     * 观察所有用户 + 选中用户 id，flatMapLatest 切换到对应档案的测量流。
     * 组合两个来源：用户列表(users) 与 当前选中 id(selected)。
     */
    val uiState: StateFlow<HistoryUiState> = combine(
        repository.observeUsers(),
        selectedUserId
    ) { users, selected -> users to selected }
        .flatMapLatest { (users, selected) ->
            // 当前激活档案（用于默认、以及高亮判断）
            val active = users.firstOrNull { it.isActive } ?: users.firstOrNull()
            // 有效选中 id：优先用户已选，其次激活档案
            val id = selected ?: active?.id
            if (id == null) {
                // 没有任何档案
                flowOf(HistoryUiState(allUsers = users, activeUser = active, selectedUserId = null))
            } else {
                repository.observeMeasurements(id).map { measurements ->
                    HistoryUiState(
                        activeUser = active,
                        allUsers = users,
                        selectedUserId = id,
                        measurements = measurements
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    /** 切换顶部查看的档案 */
    fun selectUser(userId: Long) {
        selectedUserId.value = userId
    }
}

data class HistoryUiState(
    /** 当前激活档案 */
    val activeUser: UserProfile? = null,
    /** 候选档案列表（顶部用户切换用，如 我/她） */
    val allUsers: List<UserProfile> = emptyList(),
    /** 当前选中的档案 id（null 表示跟随激活档案） */
    val selectedUserId: Long? = null,
    /** 当前选中档案的测量记录（倒序：最新在前） */
    val measurements: List<Measurement> = emptyList()
)
