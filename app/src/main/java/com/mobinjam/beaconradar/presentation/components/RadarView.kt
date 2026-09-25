package com.mobinjam.beaconradar.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// یک تابع کمکی برای محاسبه موقعیت دقیق هر نقطه روی صفحه
fun calculateDotPosition(center: Offset, maxRadius: Float, device: DeviceEntity): Offset {
    val constrainedRssi = device.rssi.coerceIn(-100, -30)
    val distanceRatio = abs(constrainedRssi + 30) / 70f
    val dotRadius = maxRadius * distanceRatio

    val angle = abs(device.macAddress.hashCode() % 360).toFloat()
    val angleInRadians = Math.toRadians(angle.toDouble() - 90.0)

    val x = center.x + (dotRadius * cos(angleInRadians)).toFloat()
    val y = center.y + (dotRadius * sin(angleInRadians)).toFloat()

    return Offset(x, y)
}

@Composable
fun RadarView(devices: List<DeviceEntity>) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRotation"
    )

    val radarColor = Color(0xFF00FF00)
    val radarBackgroundColor = Color(0xFF0A1F0A)

    // این دو متغیر برای نگه داشتن دستگاه انتخاب شده و مختصات لمس اضافه شدند
    var selectedDevice by remember { mutableStateOf<DeviceEntity?>(null) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }

    // استفاده از Box برای قرار دادن پاپ‌آپ روی بوم نقاشی
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(devices) {
                    // سیستم تشخیص کلیک (Tap)
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.width / 2f

                        // پیدا کردن دستگاهی که مختصاتش به محل کلیک ما نزدیک است
                        val clicked = devices.find { device ->
                            val pos = calculateDotPosition(center, maxRadius, device)
                            // فاصله نقطه تا محل لمس نباید بیشتر از 60 پیکسل باشد
                            hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble()) < 60.0
                        }

                        if (clicked != null) {
                            selectedDevice = clicked
                            tapOffset = calculateDotPosition(center, maxRadius, clicked)
                        } else {
                            // اگر روی فضای خالی کلیک کرد، پاپ‌آپ بسته شود
                            selectedDevice = null
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2, canvasHeight / 2)
            val maxRadius = canvasWidth / 2

            // رسم دایره‌های پس‌زمینه رادار
            drawCircle(color = radarBackgroundColor, radius = maxRadius)
            for (i in 1..3) {
                drawCircle(
                    color = radarColor.copy(alpha = 0.5f),
                    radius = maxRadius * (i / 3f),
                    style = Stroke(width = 2f)
                )
            }

            // رسم خطوط صلیبی وسط
            drawLine(
                color = radarColor.copy(alpha = 0.3f),
                start = Offset(center.x, 0f),
                end = Offset(center.x, canvasHeight),
                strokeWidth = 2f
            )
            drawLine(
                color = radarColor.copy(alpha = 0.3f),
                start = Offset(0f, center.y),
                end = Offset(canvasWidth, center.y),
                strokeWidth = 2f
            )

            // رسم خط متحرک اسکنر
            rotate(degrees = rotation, pivot = center) {
                drawLine(
                    color = radarColor,
                    start = center,
                    end = Offset(center.x, 0f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            // رسم نقطه‌های مربوط به هر دستگاه
            devices.forEach { device ->
                val pos = calculateDotPosition(center, maxRadius, device)
                val dotColor = if (device.rssi > -75) Color.Red else Color.Yellow

                // رسم هاله سفید برای دستگاهی که روی آن کلیک شده است
                if (device == selectedDevice) {
                    drawCircle(
                        color = Color.White,
                        radius = 20f,
                        center = pos,
                        style = Stroke(width = 4f)
                    )
                }

                // رسم نقطه اصلی
                drawCircle(
                    color = dotColor,
                    radius = 12f,
                    center = pos
                )
            }
        }

        // رسم پاپ‌آپ اطلاعات روی نقطه‌ی کلیک شده
        selectedDevice?.let { device ->
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(tapOffset.x.toInt(), tapOffset.y.toInt() + 20), // کمی پایین‌تر از نقطه
                onDismissRequest = { selectedDevice = null }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = device.deviceName ?: "Unknown Device",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Signal: ${device.rssi} dBm", fontSize = 12.sp)

                        device.manufacturer?.let {
                            Text(text = "Brand: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}