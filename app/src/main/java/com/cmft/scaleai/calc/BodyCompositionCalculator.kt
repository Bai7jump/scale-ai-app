package com.cmft.scaleai.calc

/**
 * 体成分计算结果
 */
data class BodyComposition(
    val weightKg: Double,
    val impedance: Double,
    val bmi: Double,
    val bodyFatPct: Double,       // 体脂率 %
    val waterPct: Double,         // 水分率 %
    val skeletalMusclePct: Double, // 骨骼肌率 %
    val muscleRatePct: Double,    // 肌肉率 %
    val bonePct: Double,          // 骨率 %
    val proteinPct: Double,       // 蛋白质 %
    val visceralFat: Double,      // 内脏脂肪
    val subcutaneousFat: Double,  // 皮下脂肪
    val ffmKg: Double,            // 去脂体重
    val bmrKcal: Int,             // 基础代谢
    val physique: String          // 体型
)

/**
 * 体成分计算引擎
 *
 * 公式基于 Omron HBF 系列公开 BIA 公式体系，经 AFU-WL-TZ-A1 实测数据校准。
 * 验证基准：(67.00kg, 559Ω, 176cm, 27岁, 男) → 体脂14.4%, BMI 21.6, BMR 1608
 */
object BodyCompositionCalculator {

    /**
     * 计算全部体成分
     * @param weightKg 体重
     * @param impedance 阻抗 Ω
     * @param heightCm 身高
     * @param age 年龄
     * @param gender "male" / "female"
     */
    fun calculate(
        weightKg: Double,
        impedance: Double,
        heightCm: Int,
        age: Int,
        gender: String
    ): BodyComposition {
        val isMale = gender == "male"
        val h = heightCm.toDouble()
        val w = weightKg
        val imp = impedance

        // BMI
        val bmi = w / ((h / 100) * (h / 100))

        // LBM 系数（去脂体重估算）
        val lbm = (h * 9.058 / 100) * (h / 100) + w * 0.32 + 12.226 - imp * 0.0068 - age * 0.0542

        // 体脂率
        val fatConst = if (isMale) 0.8 else (if (age <= 49) 9.25 else 7.25)
        var fatCoeff = 1.0
        if (isMale) {
            if (w < 61) fatCoeff = 0.98
            if (h > 160) fatCoeff *= 1.03
        } else {
            if (w > 60) fatCoeff = 0.96
            else if (w < 50) fatCoeff = 1.02
            if (h > 160) fatCoeff *= 1.03
        }
        var fatPct = (1.0 - (((lbm - fatConst) * fatCoeff) / w)) * 100
        fatPct = fatPct.coerceIn(3.0, 60.0)

        // 水分率
        val baseWater = (100 - fatPct) * 0.7
        val waterCoeff = if (baseWater <= 50) 1.02 else 0.98
        val waterPct = (baseWater * waterCoeff).coerceIn(35.0, 75.0)

        // 骨量 -> 骨率
        var boneMass = if (isMale) {
            (0.18016894 - lbm * 0.05158) * -1
        } else {
            (0.245691014 - lbm * 0.07158) * -1
        }
        if (boneMass > 2.2) boneMass += 0.1 else boneMass -= 0.1
        boneMass = boneMass.coerceAtLeast(0.5)
        val bonePct = (boneMass * 0.85 / w * 100)

        // 肌肉率
        val fatMass = fatPct * 0.01 * w
        val muscleMass = w - fatMass - boneMass * 0.85
        val muscleRatePct = muscleMass / w * 100
        val skeletalMusclePct = muscleRatePct * 0.558

        // 蛋白质
        val proteinPct = (muscleRatePct - waterPct).coerceIn(5.0, 32.0)

        // 去脂体重
        val ffm = w - fatMass

        // 基础代谢（Katch-McArdle）
        val bmr = (370 + 21.6 * ffm).toInt()

        // 内脏脂肪
        val visceralFat = (bmi * 0.3 - age * 0.05 + 0.4).coerceIn(1.0, 50.0)

        // 皮下脂肪
        val subcutaneousFat = fatPct * 0.71

        // 体型
        val physique = when {
            bmi < 18.5 -> "偏瘦"
            bmi < 24 -> "标准"
            bmi < 28 -> "超重"
            else -> "肥胖"
        }

        return BodyComposition(
            weightKg = w,
            impedance = imp,
            bmi = bmi,
            bodyFatPct = fatPct,
            waterPct = waterPct,
            skeletalMusclePct = skeletalMusclePct,
            muscleRatePct = muscleRatePct,
            bonePct = bonePct,
            proteinPct = proteinPct,
            visceralFat = visceralFat,
            subcutaneousFat = subcutaneousFat,
            ffmKg = ffm,
            bmrKcal = bmr,
            physique = physique
        )
    }
}
