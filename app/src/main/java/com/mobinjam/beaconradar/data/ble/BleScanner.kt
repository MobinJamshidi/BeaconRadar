package com.mobinjam.beaconradar.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.mobinjam.beaconradar.data.local.dao.DeviceDao
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

// کلاس فیلتر کالمن برای حذف نویز سیگنال‌های رادیویی
class KalmanFilter(private val q: Double = 0.125, private val r: Double = 4.0) {
    private var x: Double? = null
    private var p: Double = 1.0

    fun filter(measurement: Double): Double {
        if (x == null) {
            x = measurement
            return measurement
        }
        p += q
        val k = p / (p + r)
        x = x!! + k * (measurement - x!!)
        p = (1 - k) * p
        return x!!
    }
}

@SuppressLint("MissingPermission")
class BleScanner(
    context: Context,
    private val deviceDao: DeviceDao
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // نگهداری یک فیلتر کالمن اختصاصی برای هر مک‌آدرس
    private val kalmanFilters = ConcurrentHashMap<String, KalmanFilter>()

    // دیکشنری رمزگشایی برندها بر اساس استاندارد Bluetooth SIG
    // دیکشنری جامع رمزگشایی برندها بر اساس استاندارد Bluetooth SIG
    private fun decodeManufacturer(scanRecord: ScanRecord?): String? {
        val manufacturerData = scanRecord?.manufacturerSpecificData ?: return null
        if (manufacturerData.size() == 0) return null

        // استخراج شناسه شرکت (Company Identifier)
        val companyId = manufacturerData.keyAt(0)
        return when (companyId) {
            // غول‌های موبایل و تبلت
            76, 0x004C -> "Apple"
            117, 0x0075, 343, 0x0157 -> "Samsung"
            224, 0x00E0 -> "Google"
            911, 0x038F -> "Xiaomi"
            637, 0x027D -> "Huawei"
            196, 0x00C4 -> "LG"
            2542, 0x09EE -> "Nothing"

            // لپ‌تاپ و کامپیوتر
            6, 0x0006 -> "Microsoft"
            2, 0x0002 -> "Intel"
            289, 0x0121 -> "Dell"
            75, 0x004B -> "HP"
            723, 0x02D3 -> "Lenovo"

            // تجهیزات صوتی، بازی و لوازم جانبی
            301, 0x012D -> "Sony"
            158, 0x009E -> "Bose"
            304, 0x0130 -> "Sennheiser"
            140, 0x008C -> "Harman / JBL"
            300, 0x012C -> "Logitech"
            950, 0x03B6 -> "Nintendo"

            // ساعت هوشمند و سلامت
            135, 0x0087 -> "Garmin"
            552, 0x0228 -> "Fitbit"

            // لوازم خانگی و صوتی تصویری
            8, 0x0008 -> "Panasonic"
            45, 0x002D -> "Philips"

            // سازندگان چیپست (بسیاری از هندزفری‌ها یا دستگاه‌های متفرقه از این کدها استفاده می‌کنند)
            29, 0x001D -> "Qualcomm"
            87, 0x0057 -> "Realtek"
            39, 0x0027 -> "MediaTek"

            else -> "ID: $companyId" // نشان دادن کد برای برندهای ناشناس
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { scanResult ->
                val device = scanResult.device
                val address = device.address
                val rawRssi = scanResult.rssi
                val name = device.name

                // استخراج نوع دستگاه (کلاس بلوتوث)
                val deviceClass = device.bluetoothClass?.deviceClass

                val filter = kalmanFilters.getOrPut(address) { KalmanFilter() }
                val smoothedRssi = filter.filter(rawRssi.toDouble()).roundToInt()

                val manufacturerName = decodeManufacturer(scanResult.scanRecord)

                coroutineScope.launch {
                    val existingDevice = deviceDao.getDeviceByMac(address)
                    val currentTime = System.currentTimeMillis()

                    val entity = if (existingDevice != null) {
                        existingDevice.copy(
                            rssi = smoothedRssi,
                            deviceName = name ?: existingDevice.deviceName,
                            manufacturer = manufacturerName ?: existingDevice.manufacturer,
                            deviceType = deviceClass ?: existingDevice.deviceType, // آپدیت نوع دستگاه
                            lastSeen = currentTime
                        )
                    } else {
                        DeviceEntity(
                            macAddress = address,
                            deviceName = name ?: "Unknown Device",
                            rssi = smoothedRssi,
                            manufacturer = manufacturerName,
                            deviceType = deviceClass,
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
        if (currentScanner == null) return

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        currentScanner.startScan(null, scanSettings, scanCallback)
    }

    fun stopScanning() {
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }
}