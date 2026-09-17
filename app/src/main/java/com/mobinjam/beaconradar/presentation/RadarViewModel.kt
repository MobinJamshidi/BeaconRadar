package com.mobinjam.beaconradar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mobinjam.beaconradar.data.ble.BleScanner
import com.mobinjam.beaconradar.data.local.dao.DeviceDao
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RadarViewModel(
    private val bleScanner: BleScanner,
    private val deviceDao: DeviceDao
) : ViewModel() {

    // Converts the Room database Flow into a StateFlow for the Compose UI to observe in real-time
    val scannedDevices: StateFlow<List<DeviceEntity>> = deviceDao.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startRadar() {
        bleScanner.startScanning()
    }

    fun stopRadar() {
        bleScanner.stopScanning()
    }

    fun clearLog() {
        viewModelScope.launch {
            deviceDao.clearAllLog()
        }
    }
}

// Factory required to inject dependencies (BleScanner, DeviceDao) into the ViewModel
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