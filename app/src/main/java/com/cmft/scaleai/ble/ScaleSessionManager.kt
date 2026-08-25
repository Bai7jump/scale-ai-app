package com.cmft.scaleai.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 称重会话管理器：协调扫描 → 连接 → 收数据 → 完成/超时
 *
 * 状态机：
 *  Idle → Scanning → Connecting → Receiving → Done / Timeout
 *
 * 关键：收到 0x02 阻抗包（isFinal=true）即完成，数据来自该包。
 * 重量包仅作实时显示，不算完成。
 */
class ScaleSessionManager(
    private val bluetoothAdapter: BluetoothAdapter,
    private val connection: ScaleConnection
) {

    companion object {
        private const val TAG = "ScaleSession"
        private const val SCAN_TIMEOUT_MS = 60_000L      // 扫描超时 60s
        private const val DATA_TIMEOUT_MS = 45_000L      // 等数据超时 45s
        private const val SCALE_MAC = "D0:5C:00:32:47:A9"
    }

    private val _state = MutableStateFlow(ScaleSessionState.Idle)
    val state: StateFlow<ScaleSessionState> = _state

    private val _result = MutableStateFlow<ScaleReading?>(null)
    val result: StateFlow<ScaleReading?> = _result

    private var scanner: ScaleScanner? = null
    private var sessionJob: Job? = null
    private var latestWeight: Double? = null

    /**
     * 开始一次称重会话（阻塞直到完成/超时，在协程中调用）
     */
    suspend fun startSession(): ScaleReading? = withContext(Dispatchers.IO) {
        if (_state.value == ScaleSessionState.Scanning) return@withContext null
        sessionJob?.cancel()

        _state.value = ScaleSessionState.Scanning
        _result.value = null
        latestWeight = null

        // 1. 扫描
        val device = scanForDevice()
        if (device == null) {
            _state.value = ScaleSessionState.Timeout
            return@withContext null
        }

        // 2. 连接 + 收数据
        _state.value = ScaleSessionState.Receiving
        val result = waitForData(device)
        _state.value = if (result != null) ScaleSessionState.Done else ScaleSessionState.Timeout
        return@withContext result
    }

    fun cancel() {
        sessionJob?.cancel()
        scanner?.stopScan()
        connection.disconnect()
        _state.value = ScaleSessionState.Idle
    }

    /**
     * 扫描找到秤（超时返回 null）
     */
    private suspend fun scanForDevice(): BluetoothDevice? {
        scanner = ScaleScanner(bluetoothAdapter)
        val found = CompletableDeferred<BluetoothDevice>()

        val scanJob = launch(Dispatchers.IO) {
            scanner!!.foundDevices.collect { info ->
                val device = bluetoothAdapter.getRemoteDevice(info.address)
                if (device != null && !found.isCompleted) {
                    found.complete(device)
                }
            }
        }

        scanner!!.startScan()

        val device = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            found.await()
        }

        scanner!!.stopScan()
        scanJob.cancel()
        return device
    }

    /**
     * 连接并等待完成数据（0x02 阻抗包）
     */
    private suspend fun waitForData(device: BluetoothDevice): ScaleReading? {
        val done = CompletableDeferred<ScaleReading?>()

        // 订阅数据流
        val collectJob = launch(Dispatchers.IO) {
            connection.readings.collect { reading ->
                if (reading.isFinal) {
                    // 完成包：取本次体重+阻抗
                    if (!done.isCompleted) done.complete(reading)
                } else {
                    latestWeight = reading.weightKg
                }
            }
        }

        // 连接
        connection.connect(device, onPacket = {}, onDisconnect = {
            if (!done.isCompleted) done.complete(null)
        })

        // 等待完成包（超时判失败）
        val result = withTimeoutOrNull(DATA_TIMEOUT_MS) {
            done.await()
        }

        collectJob.cancel()
        connection.disconnect()
        return result
    }
}

enum class ScaleSessionState {
    Idle,
    Scanning,
    Connecting,
    Receiving,
    Done,
    Timeout
}
