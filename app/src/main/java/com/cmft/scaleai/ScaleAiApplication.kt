package com.cmft.scaleai

import android.app.Application
import com.cmft.scaleai.data.ScaleDatabase
import com.cmft.scaleai.data.ScaleRepository

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
    }
}
