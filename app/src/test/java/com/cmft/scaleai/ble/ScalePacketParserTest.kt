package com.cmft.scaleai.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ScalePacketParser 单元测试
 * 使用逆向验证的真实数据包：
 *  - 重量包 `ac29806905b80200...` → 67.00kg
 *  - 阻抗包 `ac2902000266022f01806905b8...` → 阻抗559Ω, 体重67.00kg
 */
class ScalePacketParserTest {

    private fun hex(s: String): ByteArray {
        return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Test
    fun `重量包解析出体重`() {
        // ac 29 80 69 05 b8 02 00 05 40 00 64 00 00 00 00 00 29 d5 0f
        val packet = hex("ac29806905b8020005400064000000000029d50f")
        val reading = ScalePacketParser.parsePacket(packet)
        assertEquals(67.00, reading?.weightKg ?: 0.0, 0.01)
        assertNull(reading?.impedance)
        assertEquals(false, reading?.isFinal)
    }

    @Test
    fun `阻抗包解析出阻抗和体重`() {
        // ac 29 02 00 02 66 02 2f 01 80 69 05 b8 00 00 00 00 29 d6 01
        val packet = hex("ac2902000266022f01806905b80000000029d601")
        val reading = ScalePacketParser.parsePacket(packet)
        assertEquals(67.00, reading?.weightKg ?: 0.0, 0.01)
        assertEquals(559.0, reading?.impedance ?: 0.0, 0.01)
        assertEquals(true, reading?.isFinal)
    }

    @Test
    fun `另一个重量包解析`() {
        // ac 29 00 69 06 e4 02 00 05 40 00 64 00 00 00 00 00 29 d5 1c → 67.30kg
        val packet = hex("ac29006906e4020005400064000000000029d51c")
        val reading = ScalePacketParser.parsePacket(packet)
        assertEquals(67.30, reading?.weightKg ?: 0.0, 0.01)
    }

    @Test
    fun `空包返回null`() {
        assertNull(ScalePacketParser.parsePacket(ByteArray(0)))
    }

    @Test
    fun `首字节错误的包返回null`() {
        // 首字节 0xAB 不是 0xAC
        val packet = hex("ab2900806905b8")
        assertNull(ScalePacketParser.parsePacket(packet))
    }

    @Test
    fun `过短的阻抗包返回null不崩溃`() {
        // 阻抗包但只有 8 字节（缺 data[10:13]），应返回 null 而非 ArrayIndexOutOfBounds
        val packet = hex("ac2902000266022f01")
        assertNull(ScalePacketParser.parsePacket(packet))
    }

    @Test
    fun `体重超出范围的包返回null`() {
        // raw 偏移后体重 >200kg 或 <2kg 应返回 null
        // 构造一个明显超范围的重量包: data[3:6] = 0x000000
        val packet = hex("ac29000000000000000000000000000000000000")
        assertNull(ScalePacketParser.parsePacket(packet))
    }

    @Test
    fun `未知类型返回null`() {
        // type = 0x03 未知
        val packet = hex("ac2903806905b80200")
        assertNull(ScalePacketParser.parsePacket(packet))
    }
}
