package com.cmft.scaleai.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

/**
 * 体脂秤 GATT 连接 + 数据接收
 *
 * 协议（已逆向验证）：
 *  - 订阅 notify 特征: ffb2, 1531
 *  - 握手命令: FD37000000000000 写 ffb1（response=false），秤才推数据
 */
@SuppressLint("MissingPermission")
class ScaleConnection {

    companion object {
        private const val TAG = "ScaleConnection"

        // 特征 UUID
        private val NOTIFY_FFB2 = UUID.fromString("0000ffb2-0000-1000-8000-00805f9b34fb")
        private val NOTIFY_1531 = UUID.fromString("00001531-1212-efde-1523-785feabcd123")
        private val WRITE_FFB1 = UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb")
        private val WRITE_1532 = UUID.fromString("00001532-1212-efde-1523-785feabcd123")

        // 握手命令
        private val HANDSHAKE_CMD = byteArrayOf(
            0xFD.toByte(), 0x37, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        // CCCD descriptor（启用 notify 必须写）
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ENABLE_NOTIFICATION = byteArrayOf(0x01, 0x00)
    }

    private var gatt: BluetoothGatt? = null
    private var parser: ((ByteArray) -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null

    private val _connectionState = MutableSharedFlow<ScaleConnectionState>(extraBufferCapacity = 10)
    val connectionState: SharedFlow<ScaleConnectionState> = _connectionState

    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 20)
    val readings: SharedFlow<ScaleReading> = _readings

    private var isReady = false

    /**
     * 连接体脂秤
     * @param device 目标设备
     * @param onPacket 每收到一个数据包回调
     * @param onDisconnect 断开回调
     */
    fun connect(
        device: BluetoothDevice,
        onPacket: (ByteArray) -> Unit,
        onDisconnect: () -> Unit
    ) {
        this.parser = onPacket
        this.onDisconnected = onDisconnect
        gatt = device.connectGatt(null, false, gattCallback)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isReady = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.tryEmit(ScaleConnectionState.Connected)
                    Log.d(TAG, "已连接，开始发现服务")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isReady = false
                    _connectionState.tryEmit(ScaleConnectionState.Disconnected)
                    Log.d(TAG, "已断开")
                    onDisconnected?.invoke()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "服务发现失败: status=$status")
                return
            }
            Log.d(TAG, "服务发现成功")

            // 1. 订阅 notify 特征（先订阅再握手）
            val subscribed = subscribeNotification(gatt, NOTIFY_FFB2)
            val subscribed2 = subscribeNotification(gatt, NOTIFY_1531)
            Log.d(TAG, "订阅 ffb2=$subscribed, 1531=$subscribed2")

            // 2. 握手：写 FD37 开启数据流
            val handshakeOk = sendHandshake(gatt)
            Log.d(TAG, "握手命令发送: $handshakeOk")

            isReady = true
            _connectionState.tryEmit(ScaleConnectionState.Ready)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            parser?.invoke(data)   // 交给上层解析
            _readings.tryEmit(ScalePacketParser.parsePacket(data) ?: return)
        }
    }

    /**
     * 订阅 notify 特征（setCharacteristicNotification + 写 CCCD）
     */
    private fun subscribeNotification(gatt: BluetoothGatt, charUuid: UUID): Boolean {
        val characteristic = gatt.getService(findServiceFor(charUuid))?.getCharacteristic(charUuid)
            ?: return false
        val ok = gatt.setCharacteristicNotification(characteristic, true)
        // 写 CCCD descriptor 启用 notify
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = ENABLE_NOTIFICATION
            gatt.writeDescriptor(descriptor)
        }
        return ok
    }

    /**
     * 发送握手命令（优先写 ffb1，回退 1532）
     */
    private fun sendHandshake(gatt: BluetoothGatt): Boolean {
        // 尝试 ffb1
        val write = gatt.getService(NOTIFY_FFB2)?.getCharacteristic(WRITE_FFB1)
            ?: gatt.getService(NOTIFY_1531)?.getCharacteristic(WRITE_1532)
            ?: return false
        write.value = HANDSHAKE_CMD
        write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return gatt.writeCharacteristic(write)
    }

    /**
     * 找到特征所在的服务（简化：直接尝试已知服务）
     */
    private fun findServiceFor(charUuid: UUID): UUID {
        // ffb2 在 0000ffb0 服务，1531 在 00001530 服务
        return if (charUuid.toString().startsWith("0000ffb2")) {
            UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb")
        } else {
            UUID.fromString("00001530-1212-efde-1523-785feabcd123")
        }
    }
}

/**
 * 连接状态
 */
enum class ScaleConnectionState {
    Connecting,
    Connected,
    Ready,        // 服务发现 + 订阅 + 握手完成
    Disconnected
}
