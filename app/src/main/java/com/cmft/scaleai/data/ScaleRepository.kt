package com.cmft.scaleai.data

import com.cmft.scaleai.data.dao.ChatMessageDao
import com.cmft.scaleai.data.dao.MeasurementDao
import com.cmft.scaleai.data.dao.UserProfileDao
import com.cmft.scaleai.data.entity.ChatMessage
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓库：统一封装所有数据操作
 */
class ScaleRepository(
    private val userProfileDao: UserProfileDao,
    private val measurementDao: MeasurementDao,
    private val chatMessageDao: ChatMessageDao
) {
    // ===== 用户档案 =====
    suspend fun insertUser(profile: UserProfile): Long = userProfileDao.insert(profile)
    suspend fun updateUser(profile: UserProfile) = userProfileDao.update(profile)
    suspend fun deleteUser(profile: UserProfile) = userProfileDao.delete(profile)
    fun observeUsers(): Flow<List<UserProfile>> = userProfileDao.observeAll()
    fun observeUser(id: Long): Flow<UserProfile?> = userProfileDao.observeById(id)
    suspend fun getUser(id: Long): UserProfile? = userProfileDao.getById(id)
    suspend fun getActiveUser(): UserProfile? = userProfileDao.getActive()
    suspend fun getAllUsers(): List<UserProfile> = userProfileDao.getAll()

    suspend fun setActiveUser(id: Long) {
        userProfileDao.clearActive()
        userProfileDao.setActive(id)
    }

    // ===== 称重记录 =====
    suspend fun insertMeasurement(m: Measurement): Long = measurementDao.insert(m)
    fun observeMeasurements(userId: Long): Flow<List<Measurement>> = measurementDao.observeAll(userId)
    suspend fun getLatestMeasurement(userId: Long): Measurement? = measurementDao.getLatest(userId)
    fun observeLatestMeasurement(userId: Long): Flow<Measurement?> = measurementDao.observeLatest(userId)
    suspend fun getRecentMeasurements(userId: Long, count: Int): List<Measurement> =
        measurementDao.getRecent(userId, count)
    suspend fun getMeasurement(id: Long): Measurement? = measurementDao.getById(id)
    suspend fun setReportGenerated(id: Long, generated: Boolean) =
        measurementDao.setReportGenerated(id, generated)

    // ===== AI 对话 =====
    suspend fun insertChatMessage(m: ChatMessage): Long = chatMessageDao.insert(m)
    fun observeChatMessages(userId: Long): Flow<List<ChatMessage>> = chatMessageDao.observeAll(userId)
    suspend fun getRecentChatMessages(userId: Long, count: Int): List<ChatMessage> =
        chatMessageDao.getRecent(userId, count)
    suspend fun clearChatMessages(userId: Long) = chatMessageDao.clearAll(userId)
}
