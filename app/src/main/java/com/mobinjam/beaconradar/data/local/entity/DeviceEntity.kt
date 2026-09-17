package com.mobinjam.beaconradar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_devices")
data class DeviceEntity(
    @PrimaryKey
    val macAddress: String,
    val deviceName: String?,
    val rssi: Int, // قدرت سیگنال
    val manufacturer: String?, // نام سازنده (مثل Apple, Android, ...)
    val firstSeen: Long, // زمان اولین باری که در شعاع ما دیده شد (Timestamp)
    val lastSeen: Long // زمان آخرین باری که سیگنالش دریافت شد (Timestamp)
)