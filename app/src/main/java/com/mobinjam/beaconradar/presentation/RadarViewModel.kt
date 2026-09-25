package com.mobinjam.beaconradar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobinjam.beaconradar.data.ble.BleScanner
import com.mobinjam.beaconradar.data.local.dao.DeviceDao
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RadarViewModel(
    private val bleScanner: BleScanner,
    private val deviceDao: DeviceDao
) : ViewModel() {

    // تبدیل اطلاعات دیتابیس به جریانی زنده برای رابط کاربری
    val scannedDevices: StateFlow<List<DeviceEntity>> = deviceDao.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var cleanupJob: Job? = null

    fun startRadar() {
        bleScanner.startScanning()

        // راه‌اندازی سیستم پاکسازی خودکار (Garbage Collector)
        cleanupJob = viewModelScope.launch {
            while (isActive) {
                delay(3000) // هر 3 ثانیه دیتابیس را چک می‌کند

                // دستگاه‌هایی که در 15 ثانیه گذشته دیده نشده‌اند را شناسایی کن
                val timeThreshold = System.currentTimeMillis() - 15000
                deviceDao.deleteOldDevices(timeThreshold)
            }
        }
    }

    fun stopRadar() {
        bleScanner.stopScanning()
        cleanupJob?.cancel() // توقف سیستم پاکسازی وقتی رادار خاموش است
    }

    fun clearLog() {
        viewModelScope.launch {
            deviceDao.clearAllLog()
        }
    }
}

// فکتوری برای ساخت ویومدل
class RadarViewModelFactory(
    private val bleScanner: BleScanner,
    private val deviceDao: DeviceDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RadarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RadarViewModel(bleScanner, deviceDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}