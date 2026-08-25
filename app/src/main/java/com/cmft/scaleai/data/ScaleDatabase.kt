package com.cmft.scaleai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cmft.scaleai.data.dao.ChatMessageDao
import com.cmft.scaleai.data.dao.MeasurementDao
import com.cmft.scaleai.data.dao.UserProfileDao
import com.cmft.scaleai.data.entity.ChatMessage
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile

@Database(
    entities = [UserProfile::class, Measurement::class, ChatMessage::class],
    version = 2,
    exportSchema = false
)
abstract class ScaleDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: ScaleDatabase? = null

        fun getInstance(context: Context): ScaleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScaleDatabase::class.java,
                    "scale_ai.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
