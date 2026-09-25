package com.mobinjam.beaconradar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.mobinjam.beaconradar.data.ble.BleScanner
import com.mobinjam.beaconradar.data.local.AppDatabase
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import com.mobinjam.beaconradar.presentation.RadarViewModel
import com.mobinjam.beaconradar.presentation.RadarViewModelFactory
import com.mobinjam.beaconradar.presentation.components.RadarView
import com.mobinjam.beaconradar.ui.theme.BeaconRadarTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Manual Dependency Injection: Instantiate core components
        // Note: For large-scale production apps, frameworks like Hilt or Koin are recommended.
        val database = AppDatabase.getDatabase(applicationContext)
        val bleScanner = BleScanner(applicationContext, database.deviceDao())
        val factory = RadarViewModelFactory(bleScanner, database.deviceDao())

        setContent {
            BeaconRadarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: RadarViewModel = viewModel(factory = factory)

                    // Define required permissions based on Android version
                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        listOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    } else {
                        listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    }

                    val multiplePermissionsState = rememberMultiplePermissionsState(permissionsToRequest)

                    // UI Routing based on permission state
                    if (multiplePermissionsState.allPermissionsGranted) {
                        RadarScreen(viewModel = viewModel)
                    } else {
                        PermissionScreen(permissionState = multiplePermissionsState)
                    }
                }
            }
        }
    }
}

// یادتان نرود ایمپورت زیر را به بالای فایل MainActivity.kt اضافه کنید:
// import com.mobinjam.beaconradar.presentation.components.RadarView

@Composable
fun RadarScreen(viewModel: RadarViewModel) {
    val devices by viewModel.scannedDevices.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // پنل رادار بصری (جدید)
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
                // صدا زدن کامپوزیت راداری که ساختیم
                RadarView(devices = devices)
            }
        }

        // دکمه‌های کنترل
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    isScanning = true
                    viewModel.startRadar()
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

        // لیست متنی دستگاه‌ها در پایین صفحه
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
@Composable
fun DeviceItem(device: DeviceEntity) {
    // Format the timestamp to a readable time format
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val lastSeenTime = formatter.format(Date(device.lastSeen))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.deviceName ?: "Unknown Device",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "MAC: ${device.macAddress}", style = MaterialTheme.typography.bodyMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Signal: ${device.rssi} dBm",
                    color = if (device.rssi > -60) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                Text(text = "Last seen: $lastSeenTime")
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(permissionState: MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "BeaconRadar needs Bluetooth and Location permissions to detect nearby devices.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
            Text("Grant Permissions")
        }
    }
}