package com.cmft.scaleai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cmft.scaleai.data.ScaleRepository
import com.cmft.scaleai.data.SettingsManager

/**
 * 主 ViewModel：统一管理用户切换、API Key 保存等操作
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as com.cmft.scaleai.ScaleAiApplication).repository
    private val settingsManager = SettingsManager(application)

    val users = repository.observeUsers()
    val apiKey = settingsManager.apiKey

    suspend fun setActiveUser(id: Long) {
        repository.setActiveUser(id)
    }

    suspend fun saveApiKey(key: String) {
        if (key.isNotBlank()) {
            settingsManager.saveApiKey(key)
        }
    }
}
