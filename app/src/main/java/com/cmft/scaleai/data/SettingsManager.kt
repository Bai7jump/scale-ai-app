package com.cmft.scaleai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 设置存储（DataStore）：DeepSeek API Key、首启标记等
 */
class SettingsManager(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("deepseek_api_key")
        private val FIRST_LAUNCH = stringPreferencesKey("first_launch_done")
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { it[API_KEY] }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { it[FIRST_LAUNCH].isNullOrEmpty() }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { it[FIRST_LAUNCH] = "done" }
    }
}
