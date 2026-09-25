package com.mobinjam.beaconradar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity

@Dao
interface DeviceDao {

    // اگر دستگاه جدید باشد اضافه می‌کند، اگر قبلا بوده آپدیت می‌کند (بر اساس MAC)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: DeviceEntity)

    // این دستور تمام دستگاه‌هایی که از یک زمان خاص قدیمی‌تر هستند را پاک می‌کند
    @Query("DELETE FROM scanned_devices WHERE lastSeen < :timeThreshold")
    suspend fun deleteOldDevices(timeThreshold: Long)

    // دریافت لیست تمام دستگاه‌ها به صورت زنده (مرتب‌شده بر اساس آخرین بازدید)
    @Query("SELECT * FROM scanned_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    // جستجوی یک دستگاه خاص بر اساس مک‌آدرس
    @Query("SELECT * FROM scanned_devices WHERE macAddress = :mac LIMIT 1")
    suspend fun getDeviceByMac(mac: String): DeviceEntity?

    // پاکسازی کل تاریخچه (برای تنظیمات امنیتی اپلیکیشن)
    @Query("DELETE FROM scanned_devices")
    suspend fun clearAllLog()
}