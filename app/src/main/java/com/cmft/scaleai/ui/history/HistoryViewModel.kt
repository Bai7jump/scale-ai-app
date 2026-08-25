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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 历史页 ViewModel：观察当前用户 + 其测量记录
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as com.cmft.scaleai.ScaleAiApplication).repository

    private val activeUser = MutableStateFlow<UserProfile?>(null)

    init {
        viewModelScope.launch {
            repository.getActiveUser()?.let { activeUser.value = it }
        }
    }

    val uiState: StateFlow<HistoryUiState> = repository.observeUsers()
        .combine(repository.observeMeasurements(activeUser.value?.id ?: -1)) { users, measurements ->
            HistoryUiState(
                activeUser = activeUser.value,
                measurements = measurements
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())
}

data class HistoryUiState(
    val activeUser: UserProfile? = null,
    val measurements: List<Measurement> = emptyList()
)
