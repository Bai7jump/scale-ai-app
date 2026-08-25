package com.cmft.scaleai

import android.app.Application
import com.cmft.scaleai.data.ScaleDatabase
import com.cmft.scaleai.data.ScaleRepository
import com.cmft.scaleai.data.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用入口：初始化数据库和仓库
 */
class ScaleAiApplication : Application() {

    lateinit var repository: ScaleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = ScaleDatabase.getInstance(this)
        repository = ScaleRepository(
            userProfileDao = database.userProfileDao(),
            measurementDao = database.measurementDao(),
            chatMessageDao = database.chatMessageDao()
        )

        // 开发模式：首启填充假数据（正式版移除）
        if (BuildConfig.DEBUG) {
            CoroutineScope(Dispatchers.IO).launch {
                SeedData.seed(repository)
            }
        }
    }
}
