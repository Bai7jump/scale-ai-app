package com.cmft.scaleai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户档案（一男一女两个档案）
 */
@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,               // 名字（如"我"、"她"）
    val gender: String,             // "male" / "female"
    val heightCm: Int,              // 身高 cm
    val age: Int,                   // 年龄
    val targetWeightKg: Double?,    // 目标体重（可空）
    val targetBodyFatPct: Double?,  // 目标体脂率（可空）
    val baselineWeightKg: Double?,  // 基准体重（双人匹配用，可空）
    val isActive: Boolean = false   // 当前激活档案
)
