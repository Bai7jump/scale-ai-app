package com.cmft.scaleai.ble

/**
 * 体脂秤数据包解析结果
 * @param weightKg 体重 kg
 * @param impedance 阻抗 Ω（仅阻抗包有值）
 * @param isFinal 是否完成包（阻抗包=true，表示本次测量完成）
 */
data class ScaleReading(
    val weightKg: Double,
    val impedance: Double? = null,
    val isFinal: Boolean = false
)

/**
 * 蚂蚁阿福体脂秤 AFU-WL-TZ-A1 数据包解析器
 *
 * 协议（已逆向验证）：
 *  - 数据包首字节固定 0xAC
 *  - type 0x00/0x80（实时重量包）：data[3:6] = 24位大端体重 raw，weight=(raw-6815744)/1000
 *  - type 0x02（阻抗完成包）：data[6:8] = 阻抗Ω（直接值），data[10:13] = 体重raw（同偏移）
 *
 * 纯函数，无 Android 依赖，可单元测试。
 */
object ScalePacketParser {

    private const val WEIGHT_OFFSET = 6815744
    private const val MIN_WEIGHT = 2.0
    private const val MAX_WEIGHT = 200.0

    /**
     * 解析一个数据包。非法/截断包返回 null（不抛异常）。
     */
    fun parsePacket(data: ByteArray): ScaleReading? {
        // 首字节必须是 0xAC，且至少 6 字节（重量包最小长度）
        if (data.size < 6 || data[0] != 0xAC.toByte()) return null

        val type = data[2].toInt() and 0xFF
        return when (type) {
            0x00, 0x80 -> {
                // 实时重量包：data[3:6] = 24位大端体重 raw
                val raw = read24(data, 3)
                val weight = (raw - WEIGHT_OFFSET) / 1000.0
                if (weight in MIN_WEIGHT..MAX_WEIGHT) {
                    ScaleReading(weightKg = weight, isFinal = false)
                } else {
                    null
                }
            }
            0x02 -> {
                // 阻抗完成包：需至少 13 字节（读 data[6:8] 和 data[10:13]）
                if (data.size < 13) return null
                val impedance = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
                val raw = read24(data, 10)
                val weight = (raw - WEIGHT_OFFSET) / 1000.0
                if (weight in MIN_WEIGHT..MAX_WEIGHT) {
                    ScaleReading(weightKg = weight, impedance = impedance.toDouble(), isFinal = true)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * 读取 24 位大端整数（offset 起 3 字节）。
     */
    private fun read24(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)
    }
}
