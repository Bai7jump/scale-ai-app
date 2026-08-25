package com.cmft.scaleai.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 体脂秤 BLE 扫描器
 * 扫描并过滤 AFU-WL-TZ-A1（按设备名 + MAC 地址匹配）
 */
class ScaleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "ScaleScanner"
        private const val SCALE_NAME = "AFU-WL-TZ-A1"
        private const val SCALE_MAC = "D0:5C:00:32:47:A9"
    }

    private val _foundDevices = MutableSharedFlow<BluetoothDeviceInfo>(extraBufferCapacity = 10)
    val foundDevices: SharedFlow<BluetoothDeviceInfo> = _foundDevices

    private var isScanning = false
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: ""
            val address = device.address ?: ""

            // 按设备名 或 MAC 匹配（MAC 可能因地址随机化变化，名字为主）
            val isMatch = name.contains(SCALE_NAME) || address.equals(SCALE_MAC, ignoreCase = true)
            if (isMatch) {
                _foundDevices.tryEmit(
                    BluetoothDeviceInfo(
                        name = name,
                        address = address,
                        rssi = result.rssi
                    )
                )
                Log.d(TAG, "发现体脂秤: $name ($address) rssi=${result.rssi}")
                stopScan()  // 发现即停
            }
        }
    }

    fun startScan() {
        if (isScanning) return
        if (bluetoothAdapter.isEnabled) {
            // SCAN_MODE_LOW_LATENCY: 低延迟高频率扫描（称重广播窗口短，需快速发现）
            bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
            isScanning = true
            Log.d(TAG, "开始扫描")
        }
    }

    fun stopScan() {
        if (isScanning) {
            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
            isScanning = false
            Log.d(TAG, "停止扫描")
        }
    }
}

/**
 * 发现的 BLE 设备信息
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int
)
