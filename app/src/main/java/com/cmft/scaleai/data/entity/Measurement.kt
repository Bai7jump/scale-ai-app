package com.cmft.scaleai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 称重记录（BLE采集 或 手动输入）
 * 体成分字段可空：手动输入时只有体重
 */
@Entity(
    tableName = "measurements",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class Measurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,               // 归属档案
    val timestamp: Long,            // 时间戳(ms)
    val weightKg: Double,           // 体重 kg（必填）
    val impedance: Double?,         // 阻抗 Ω（BLE才有）
    val bodyFatPct: Double?,        // 体脂率 %
    val waterPct: Double?,          // 水分率 %
    val muscleRatePct: Double?,     // 肌肉率 %
    val bonePct: Double?,           // 骨率 %
    val proteinPct: Double?,        // 蛋白质 %
    val visceralFat: Double?,       // 内脏脂肪
    val bmrKcal: Int?,              // 基础代谢
    val bmi: Double?,               // BMI
    val source: String,             // "ble" / "manual"
    val reportGenerated: Boolean = false  // AI报告是否已生成
)
