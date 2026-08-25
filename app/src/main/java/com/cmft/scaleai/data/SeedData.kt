package com.cmft.scaleai.data

import com.cmft.scaleai.calc.BodyCompositionCalculator
import com.cmft.scaleai.data.entity.Measurement
import com.cmft.scaleai.data.entity.UserProfile
import java.util.Calendar

/**
 * 假数据生成器：造一批合理的称重记录 + 档案，方便开发期预览
 * 之后接入真机数据后移除
 */
object SeedData {

    /**
     * 生成两个档案（男女）+ 各 14 天历史称重记录
     */
    suspend fun seed(repository: ScaleRepository) {
        // 已有用户则不重复造
        if (repository.getAllUsers().isNotEmpty()) return

        // 男档案：27岁 176cm 目标增肌
        val maleId = repository.insertUser(
            UserProfile(
                name = "我", gender = "male", heightCm = 176, age = 27,
                targetWeightKg = 68.0, targetBodyFatPct = 13.0,
                baselineWeightKg = 65.3, isActive = true
            )
        )

        // 女档案：30岁 162cm
        val femaleId = repository.insertUser(
            UserProfile(
                name = "她", gender = "female", heightCm = 162, age = 30,
                targetWeightKg = 50.0, targetBodyFatPct = 22.0,
                baselineWeightKg = 52.0, isActive = false
            )
        )

        // 14 天男性体重从 65.5 缓慢增到 66.8（阻抗 550-570Ω）
        seedMeasurements(repository, maleId, startWeight = 65.5, endWeight = 66.8, impedance = 560.0, gender = "male", height = 176, age = 27)
        // 14 天女性体重 52.3 → 51.8（阻抗 720Ω）
        seedMeasurements(repository, femaleId, startWeight = 52.3, endWeight = 51.8, impedance = 720.0, gender = "female", height = 162, age = 30)
    }

    private suspend fun seedMeasurements(
        repository: ScaleRepository,
        userId: Long,
        startWeight: Double,
        endWeight: Double,
        impedance: Double,
        gender: String,
        height: Int,
        age: Int
    ) {
        val cal = Calendar.getInstance()
        val days = 14
        for (i in days downTo 1) {
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 7)
            cal.set(Calendar.MINUTE, 30)

            // 线性插值 + 轻微随机波动
            val progress = (days - i).toDouble() / days
            val base = startWeight + (endWeight - startWeight) * progress
            val weight = base + (Math.random() - 0.5) * 0.15

            // 阻抗随体重微调
            val imp = impedance + (Math.random() - 0.5) * 10

            val comp = BodyCompositionCalculator.calculate(weight, imp, height, age, gender)

            repository.insertMeasurement(
                Measurement(
                    userId = userId,
                    timestamp = cal.timeInMillis,
                    weightKg = weight,
                    impedance = imp,
                    bodyFatPct = comp.bodyFatPct,
                    waterPct = comp.waterPct,
                    muscleRatePct = comp.muscleRatePct,
                    bonePct = comp.bonePct,
                    proteinPct = comp.proteinPct,
                    visceralFat = comp.visceralFat,
                    bmrKcal = comp.bmrKcal,
                    bmi = comp.bmi,
                    source = "seed",
                    reportGenerated = i == 1
                )
            )
        }
    }
}
