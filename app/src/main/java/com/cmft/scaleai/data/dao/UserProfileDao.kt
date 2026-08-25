package com.cmft.scaleai.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cmft.scaleai.data.entity.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Insert
    suspend fun insert(profile: UserProfile): Long

    @Update
    suspend fun update(profile: UserProfile)

    @Delete
    suspend fun delete(profile: UserProfile)

    @Query("SELECT * FROM user_profiles ORDER BY id")
    fun observeAll(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getById(id: Long): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): UserProfile?

    @Query("SELECT * FROM user_profiles")
    suspend fun getAll(): List<UserProfile>

    @Query("UPDATE user_profiles SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE user_profiles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)
}
