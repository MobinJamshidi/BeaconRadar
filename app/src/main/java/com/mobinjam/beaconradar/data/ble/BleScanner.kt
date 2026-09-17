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

// Suppressing MissingPermission since runtime permissions are explicitly handled
// at the UI (Compose) layer before invoking startScanning().
@SuppressLint("MissingPermission")
class BleScanner(
    context: Context,
    private val deviceDao: DeviceDao
) {
    // Bluetooth system services setup
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    // Coroutine scope for background database operations (off the main thread)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Callback triggered whenever a BLE device is discovered nearby
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            result?.let { scanResult ->
                val device = scanResult.device
                val rssi = scanResult.rssi
                val name = device.name
                val address = device.address

                // Process and save the device into the local database asynchronously
                coroutineScope.launch {
                    val existingDevice = deviceDao.getDeviceByMac(address)
                    val currentTime = System.currentTimeMillis()

                    val entity = if (existingDevice != null) {
                        // Update existing device record (e.g., refresh RSSI and timestamp)
                        existingDevice.copy(
                            rssi = rssi,
                            // Retain previous name if the current broadcast doesn't include it
                            deviceName = name ?: existingDevice.deviceName,
                            lastSeen = currentTime
                        )
                    } else {
                        // Register a newly discovered device
                        DeviceEntity(
                            macAddress = address,
                            deviceName = name ?: "Unknown Device",
                            rssi = rssi,
                            manufacturer = null, // TODO: Decode manufacturer from scanRecord bytes later
                            firstSeen = currentTime,
                            lastSeen = currentTime
                        )
                    }

                    deviceDao.upsertDevice(entity)
                }
            }
        }

        // Handle scan failures (e.g., hardware constraints or unexpected permission drops)
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // TODO: Expose error states to the ViewModel/UI
        }
    }

    // Starts the BLE radar
    fun startScanning() {
        scanner?.startScan(scanCallback)
    }

    // Stops the BLE radar to prevent battery drain
    fun stopScanning() {
        scanner?.stopScan(scanCallback)
    }
}