package com.mobinjam.beaconradar.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.mobinjam.beaconradar.data.local.dao.DeviceDao
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class BleScanner(
    context: Context,
    private val deviceDao: DeviceDao
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { scanResult ->
                val device = scanResult.device
                val address = device.address
                val rssi = scanResult.rssi
                val name = device.name

                // لاگ ساده برای بررسی صحت کار
                Log.d("BleRadar", "Target spotted: MAC=$address | RSSI=$rssi")

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
            Log.e("BleRadar", "Scan failed! Error Code: $errorCode")
        }
    }

    fun startScanning() {
        val currentScanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (currentScanner == null) {
            Log.e("BleRadar", "Bluetooth is OFF or scanner unavailable.")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        currentScanner.startScan(null, scanSettings, scanCallback)
        Log.d("BleRadar", "Radar engine started.")
    }

    fun stopScanning() {
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }
}