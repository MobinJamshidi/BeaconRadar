package com.mobinjam.beaconradar

import android.Manifest
import android.bluetooth.BluetoothClass
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobinjam.beaconradar.data.ble.BleScanner
// نام AppDatabase را در صورت نیاز با نام دیتابیس خود جایگزین کنید
import com.mobinjam.beaconradar.data.local.AppDatabase
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import com.mobinjam.beaconradar.presentation.RadarViewModel
import com.mobinjam.beaconradar.presentation.RadarViewModelFactory
import com.mobinjam.beaconradar.presentation.components.RadarView
import com.mobinjam.beaconradar.ui.theme.BeaconRadarTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val deviceDao = database.deviceDao()
        val bleScanner = BleScanner(applicationContext, deviceDao)
        val factory = RadarViewModelFactory(bleScanner, deviceDao)

        setContent {
            BeaconRadarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val radarViewModel: RadarViewModel = viewModel(factory = factory)
                    RadarScreen(viewModel = radarViewModel)
                }
            }
        }
    }
}

@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val devices by viewModel.scannedDevices.collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // لیست مجوزهای مورد نیاز بر اساس نسخه اندروید
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // سیستم درخواست مجوز از کاربر در لحظه
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            isScanning = true
            viewModel.startRadar()
        } else {
            Toast.makeText(context, "برای فعالیت رادار، مجوز بلوتوث الزامی است", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RadarView(devices = devices)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    // قبل از شروع اسکن، ابتدا مجوزها را چک می‌کند
                    permissionLauncher.launch(permissionsToRequest)
                },
                enabled = !isScanning
            ) {
                Text("Start Scan")
            }

            Button(
                onClick = {
                    isScanning = false
                    viewModel.stopRadar()
                },
                enabled = isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Stop Scan")
            }

            OutlinedButton(onClick = { viewModel.clearLog() }) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices, key = { it.macAddress }) { device ->
                DeviceItem(device = device)
            }
        }
    }
}

fun calculateDistance(rssi: Int): Double {
    val txPower = -59.0
    val n = 2.5
    return 10.0.pow((txPower - rssi) / (10 * n))
}

fun getDeviceIcon(deviceType: Int?): ImageVector {
    if (deviceType == null) return Icons.Default.Bluetooth

    return when (deviceType) {
        BluetoothClass.Device.PHONE_SMART, BluetoothClass.Device.PHONE_CELLULAR -> Icons.Default.Smartphone
        BluetoothClass.Device.COMPUTER_LAPTOP, BluetoothClass.Device.COMPUTER_DESKTOP -> Icons.Default.Computer
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> Icons.Default.Headphones
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> Icons.Default.Speaker
        BluetoothClass.Device.WEARABLE_WRIST_WATCH -> Icons.Default.Watch
        BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER -> Icons.Default.Tv
        else -> Icons.Default.Bluetooth
    }
}

@Composable
fun DeviceItem(device: DeviceEntity) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val lastSeenTime = formatter.format(Date(device.lastSeen))
    val distance = calculateDistance(device.rssi)
    val distanceText = String.format(Locale.US, "%.1f m", distance)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getDeviceIcon(device.deviceType),
                        contentDescription = "Device Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = device.deviceName ?: "Unknown Device",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (device.manufacturer != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = device.manufacturer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "MAC: ${device.macAddress}", style = MaterialTheme.typography.bodyMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${device.rssi} dBm  (≈ $distanceText)",
                    fontWeight = FontWeight.SemiBold,
                    color = if (device.rssi > -75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
                Text(text = "Last: $lastSeenTime", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}