package com.cmft.scaleai.calc

import com.cmft.scaleai.data.entity.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UserMatcher 单元测试
 */
class UserMatcherTest {

    private fun user(id: Long, name: String, gender: String, baseline: Double?) = UserProfile(
        id = id, name = name, gender = gender, heightCm = 170, age = 25,
        targetWeightKg = null, targetBodyFatPct = null, baselineWeightKg = baseline,
        isActive = false
    )

    // 无历史时的兜底：返回 null（用 baselineWeightKg）
    private val noHistory: (Long) -> Double? = { null }

    @Test
    fun `差异大时高置信建议男`() {
        // 男基准67, 女基准55, 体重66.8 → 高置信男
        val male = user(1, "男", "male", 67.0)
        val female = user(2, "女", "female", 55.0)
        val result = UserMatcher.match(66.8, null, listOf(male, female), male, noHistory)
        assertEquals(male.id, result.user.id)
        assertTrue("差异大应高置信", result.confidence)
    }

    @Test
    fun `体重相近时低置信`() {
        // 男基准67, 女基准66.5（相近）, 体重66.6 → 低置信
        val male = user(1, "男", "male", 67.0)
        val female = user(2, "女", "female", 66.5)
        val result = UserMatcher.match(66.6, null, listOf(male, female), male, noHistory)
        assertFalse("体重相近应低置信", result.confidence)
    }

    @Test
    fun `单用户直接高置信`() {
        val male = user(1, "男", "male", 67.0)
        val result = UserMatcher.match(68.0, null, listOf(male), male, noHistory)
        assertEquals(male.id, result.user.id)
        assertTrue(result.confidence)
    }

    @Test
    fun `全部无基准时用激活用户低置信`() {
        val male = user(1, "男", "male", null)
        val female = user(2, "女", "female", null)
        val result = UserMatcher.match(66.0, null, listOf(male, female), male, noHistory)
        assertEquals(male.id, result.user.id)
        assertFalse(result.confidence)
    }

    @Test
    fun `有历史均值时用均值匹配`() {
        val male = user(1, "男", "male", null)
        val female = user(2, "女", "female", null)
        // 历史均值：男67, 女55
        val history: (Long) -> Double? = { id -> if (id == 1L) 67.0 else 55.0 }
        val result = UserMatcher.match(66.8, null, listOf(male, female), male, history)
        assertEquals(male.id, result.user.id)
        assertTrue(result.confidence)
    }
}
