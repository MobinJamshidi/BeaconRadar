package com.mobinjam.beaconradar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// نام جدول دقیقاً به چیزی که Dao انتظار دارد تغییر کرد
@Entity(tableName = "scanned_devices")
data class DeviceEntity(
    @PrimaryKey val macAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val manufacturer: String?,
    val deviceType: Int?,
    val firstSeen: Long,
    val lastSeen: Long
)