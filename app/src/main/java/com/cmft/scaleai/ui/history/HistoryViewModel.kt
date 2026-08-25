package com.cmft.scaleai.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as com.cmft.scaleai.ScaleAiApplication).repository

    // 观察所有用户，取当前激活用户，再查该用户的测量
    val uiState: StateFlow<HistoryUiState> = repository.observeUsers()
        .flatMapLatest { users ->
            val active = users.firstOrNull { it.isActive } ?: users.firstOrNull()
            if (active == null) {
                flowOf(HistoryUiState())
            } else {
                repository.observeMeasurements(active.id).map { measurements ->
                    HistoryUiState(activeUser = active, measurements = measurements)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())
}

data class HistoryUiState(
    val activeUser: UserProfile? = null,
    val measurements: List<Measurement> = emptyList()
)
