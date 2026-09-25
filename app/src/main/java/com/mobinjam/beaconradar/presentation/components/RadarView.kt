package com.mobinjam.beaconradar.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.mobinjam.beaconradar.data.local.entity.DeviceEntity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2, canvasHeight / 2)
        val maxRadius = canvasWidth / 2

        drawCircle(color = radarBackgroundColor, radius = maxRadius)
        for (i in 1..3) {
            drawCircle(
                color = radarColor.copy(alpha = 0.5f),
                radius = maxRadius * (i / 3f),
                style = Stroke(width = 2f)
            )
        }

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

        rotate(degrees = rotation, pivot = center) {
            drawLine(
                color = radarColor,
                start = center,
                end = Offset(center.x, 0f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        devices.forEach { device ->
            val constrainedRssi = device.rssi.coerceIn(-100, -30)
            val distanceRatio = abs(constrainedRssi + 30) / 70f
            val dotRadius = maxRadius * distanceRatio

            val angle = abs(device.macAddress.hashCode() % 360).toFloat()
            val angleInRadians = Math.toRadians(angle.toDouble() - 90.0)

            val x = center.x + (dotRadius * cos(angleInRadians)).toFloat()
            val y = center.y + (dotRadius * sin(angleInRadians)).toFloat()

// کالیبره جدید: بالای 75- را به عنوان دستگاه نزدیک و قرمز در نظر می‌گیریم
            val dotColor = if (device.rssi > -75) Color.Red else Color.Yellow

            drawCircle(
                color = dotColor,
                radius = 12f,
                center = Offset(x, y)
            )
            drawCircle(
                color = dotColor,
                radius = 12f,
                center = Offset(x, y)
            )
        }
    }
}