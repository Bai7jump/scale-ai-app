package com.cmft.scaleai.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BodyCompositionCalculator 单元测试
 * 用已验证的基准数据锁定公式：防止回归
 */
class BodyCompositionCalculatorTest {

    @Test
    fun `男性基准数据计算正确`() {
        // (67.00kg, 559Ω, 176cm, 27岁, 男) → 体脂14.4%, BMI 21.6, BMR 1608
        val result = BodyCompositionCalculator.calculate(67.00, 559.0, 176, 27, "male")
        assertEquals(14.4, result.bodyFatPct, 0.1)
        assertEquals(21.6, result.bmi, 0.1)
        assertEquals(1608, result.bmrKcal)
        assertEquals("标准", result.physique)
    }

    @Test
    fun `女性基准数据合理`() {
        // (55kg, 700Ω, 162cm, 30岁, 女) → 体脂 25-32% 区间
        val result = BodyCompositionCalculator.calculate(55.0, 700.0, 162, 30, "female")
        assertTrue("女性体脂应在25-32%, 实际 ${result.bodyFatPct}", result.bodyFatPct in 25.0..32.0)
        assertTrue(result.bmrKcal > 0)
    }

    @Test
    fun `体重体重和阻抗传递正确`() {
        val result = BodyCompositionCalculator.calculate(70.0, 500.0, 180, 25, "male")
        assertEquals(70.0, result.weightKg, 0.01)
        assertEquals(500.0, result.impedance, 0.01)
    }

    @Test
    fun `体脂率在合理范围`() {
        // 各种输入，体脂率都应 clamp 在 3-60
        val r1 = BodyCompositionCalculator.calculate(50.0, 300.0, 170, 20, "male")
        val r2 = BodyCompositionCalculator.calculate(90.0, 900.0, 160, 60, "female")
        assertTrue(r1.bodyFatPct in 3.0..60.0)
        assertTrue(r2.bodyFatPct in 3.0..60.0)
    }

    @Test
    fun `BMI和体型分类正确`() {
        assertEquals("偏瘦", BodyCompositionCalculator.calculate(50.0, 500.0, 180, 25, "male").physique)
        assertEquals("标准", BodyCompositionCalculator.calculate(68.0, 500.0, 175, 25, "male").physique)
        assertEquals("超重", BodyCompositionCalculator.calculate(80.0, 500.0, 175, 25, "male").physique)
        assertEquals("肥胖", BodyCompositionCalculator.calculate(95.0, 500.0, 175, 25, "male").physique)
    }
}
