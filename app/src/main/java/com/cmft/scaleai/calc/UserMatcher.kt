package com.cmft.scaleai.calc

import com.cmft.scaleai.data.entity.UserProfile

/**
 * 双人匹配结果
 * @param user 建议的用户
 * @param confidence 置信度（true=高置信可默认选中，false=低置信需用户确认）
 * @param weightDiff 与建议用户基准体重的差值
 */
data class UserMatch(
    val user: UserProfile,
    val confidence: Boolean,
    val weightDiff: Double
)

/**
 * 双人匹配逻辑
 *
 * 策略：
 * 1. 有历史记录 → 用最近 5 次体重均值差，差小者优先
 * 2. 无历史 → 用 baselineWeightKg 基准体重
 * 3. 阻抗辅助信号（弱）：>600Ω 偏女、<600Ω 偏男，仅低置信时参考
 * 4. 置信度：两位候选差值 < 0.5kg → 低置信（弹窗确认）
 * 5. 无任何基准 → 默认当前激活用户 + 低置信
 */
object UserMatcher {

    private const val CONFIDENCE_THRESHOLD = 0.5  // kg
    private const val IMPEDANCE_FEMALE_THRESHOLD = 600.0  // Ω

    /**
     * 匹配建议用户
     * @param weightKg 本次体重
     * @param impedance 本次阻抗（可空）
     * @param users 候选用户列表
     * @param activeUser 当前激活用户（兜底）
     * @param getAvgWeight 获取某用户的平均体重（有历史时）
     */
    fun match(
        weightKg: Double,
        impedance: Double?,
        users: List<UserProfile>,
        activeUser: UserProfile?,
        getAvgWeight: (Long) -> Double?
    ): UserMatch {
        if (users.isEmpty()) error("无用户档案")
        if (users.size == 1) {
            return UserMatch(users[0], confidence = true, weightDiff = 0.0)
        }

        // 计算每个用户与本次体重的差距
        val candidates = users.map { user ->
            val baseWeight = getAvgWeight(user.id) ?: user.baselineWeightKg
            val diff = baseWeight?.let { Math.abs(weightKg - it) } ?: Double.MAX_VALUE
            Triple(user, baseWeight, diff)
        }

        // 排除无基准的用户（baseWeight 为 null 的）
        val withBaseline = candidates.filter { it.second != null }
        if (withBaseline.isEmpty()) {
            // 全无基准 → 默认激活用户 + 低置信
            val fallback = activeUser ?: users.first()
            return UserMatch(fallback, confidence = false, weightDiff = 0.0)
        }

        // 按差值排序
        val sorted = withBaseline.sortedBy { it.third }
        val best = sorted[0]

        // 判断置信度
        var confidence = true
        if (sorted.size >= 2) {
            val diffBest = sorted[0].third
            val diffSecond = sorted[1].third
            // 两位差值接近 → 低置信
            if (Math.abs(diffBest - diffSecond) < CONFIDENCE_THRESHOLD) {
                confidence = false
            }
        }

        // 阻抗辅助信号：低置信时，若阻抗明显偏女性，调整建议
        if (!confidence && impedance != null) {
            val bestUser = best.first
            val impedanceSuggestsFemale = impedance > IMPEDANCE_FEMALE_THRESHOLD
            if (impedanceSuggestsFemale && bestUser.gender == "male") {
                // 阻抗偏女，但体重匹配的是男 → 仍保持低置信（由用户确认）
                // 不做自动切换，避免误判
            }
        }

        return UserMatch(best.first, confidence, best.third)
    }
}
