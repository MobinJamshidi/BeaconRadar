package com.mobinjam.beaconradar.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.mobinjam.beaconradar.data.local.dao.DeviceDao
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// از آنجا که ما مجوزها را مستقیماً در رابط کاربری (Compose) از کاربر می‌گیریم و بعد اسکن را شروع می‌کنیم،
// با این انوتیشن به اندروید استودیو می‌گوییم که نگران مجوز در این کلاس نباشد.
@SuppressLint("MissingPermission")
class BleScanner(
    context: Context,
    private val deviceDao: DeviceDao
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            result?.let { scanResult ->
                val device = scanResult.device
                val rssi = scanResult.rssi
                val name = device.name
                val address = device.address

                coroutineScope.launch {
                    val existingDevice = deviceDao.getDeviceByMac(address)
                    val currentTime = System.currentTimeMillis()

                    val entity = if (existingDevice != null) {
                        existingDevice.copy(
                            rssi = rssi,
                            deviceName = name ?: existingDevice.deviceName,
                            lastSeen = currentTime
                        )
                    } else {
                        DeviceEntity(
                            macAddress = address,
                            deviceName = name ?: "Unknown Device",
                            rssi = rssi,
                            manufacturer = null,
                            firstSeen = currentTime,
                            lastSeen = currentTime
                        )
                    }

                    deviceDao.upsertDevice(entity)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    fun startScanning() {
        scanner?.startScan(scanCallback)
    }

    fun stopScanning() {
        scanner?.stopScan(scanCallback)
    }
}