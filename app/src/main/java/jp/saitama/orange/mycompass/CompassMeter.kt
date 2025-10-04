package jp.saitama.orange.mycompass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

import androidx.compose.foundation.Image
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

@Composable
fun CompassMeter(
    azimuth: Float,
    destinationInfoList: List<DestinationInfo> = emptyList(),
    modifier: Modifier = Modifier,
    pitch: Float = 0f,
    roll: Float = 0f,
    headingAccuracyDeg: Float? = null,
    sensorAccuracy: Int? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${azimuth.toInt()}°",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            val boxSizePx = constraints.maxWidth.toFloat()
            val density = LocalDensity.current.density

            // Rotating container for the compass image only
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(-azimuth)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.compass_photo),
                    contentDescription = "Compass Image",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay rotated with the compass: draw destinations by absolute magnetic bearing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(-azimuth)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.width / 2

                    destinationInfoList.forEach { destInfo ->
                        val angleRad = Math.toRadians(destInfo.bearing.toDouble() - 90)

                        val outerX = centerX + radius * cos(angleRad).toFloat()
                        val outerY = centerY + radius * sin(angleRad).toFloat()
                        val innerX = centerX + (radius - size.width * 0.15f) * cos(angleRad).toFloat()
                        val innerY = centerY + (radius - size.height * 0.15f) * sin(angleRad).toFloat()

                        drawLine(
                            color = Color.Blue,
                            start = Offset(x = outerX, y = outerY),
                            end = Offset(x = innerX, y = innerY),
                            strokeWidth = 8f
                        )
                    }
                }

                destinationInfoList.forEach { destInfo ->
                    val angleRad = Math.toRadians(destInfo.bearing.toDouble() - 90)
                    val centerX = boxSizePx / 2f
                    val centerY = boxSizePx / 2f

                    val margin = 16 * density
                    val flagRadius = (boxSizePx / 2f) + margin

                    val flagXPx = centerX + flagRadius * cos(angleRad).toFloat()
                    val flagYPx = centerY + flagRadius * sin(angleRad).toFloat()

                    Column(
                        modifier = Modifier.offset {
                            IntOffset(
                                x = (flagXPx - 12 * density).roundToInt(),
                                y = (flagYPx - 12 * density).roundToInt()
                            )
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = destInfo.destination.name,
                            tint = Color.Blue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = destInfo.destination.name,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Subtle tilt indicator: small circle with a dot showing pitch/roll
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                val sizeDp = 72.dp
                val strokeWidth = 2.dp
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(sizeDp)
                ) {
                    val tiltDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // Outer circle (subtle)
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.25f),
                            radius = r,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx())
                        )

                        // Crosshair
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.2f),
                            start = Offset(center.x - r, center.y),
                            end = Offset(center.x + r, center.y),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.2f),
                            start = Offset(center.x, center.y - r),
                            end = Offset(center.x, center.y + r),
                            strokeWidth = 1f
                        )

                        // Map pitch/roll (deg) to dot position. Edge ≈ 45°
                        val maxAngle = 45f
                        val nx = (roll / maxAngle).coerceIn(-1f, 1f)
                        val ny = (-pitch / maxAngle).coerceIn(-1f, 1f)
                        var dx = nx * r
                        var dy = ny * r
                        // Clamp to circle boundary if outside
                        val len = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (len > r) {
                            val scale = r / len
                            dx *= scale
                            dy *= scale
                        }

                        // Dot
                        drawCircle(
                            color = tiltDotColor,
                            radius = r * 0.08f,
                            center = Offset(center.x + dx, center.y + dy)
                        )
                    }
                }
            }

            // Draw north marker (red line, fixed upward)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                drawLine(
                    color = Color.Red,
                    start = Offset(x = centerX, y = 0f),
                    end = Offset(x = centerX, y = size.height * 0.15f),
                    strokeWidth = 8f
                )
            }

            // Small accuracy chip (bottom-start)
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                val accText = when {
                    headingAccuracyDeg != null -> "精度 ${qualityFromSigma(headingAccuracyDeg)}"
                    sensorAccuracy != null -> when (sensorAccuracy) {
                        3 -> "精度 良"
                        2 -> "精度 中"
                        else -> "精度 低"
                    }
                    else -> null
                }
                accText?.let { txt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = txt,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Display destination info
        if (destinationInfoList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DestinationInfoDisplay(destinationInfoList = destinationInfoList)
        }
    }
}

@Composable
fun DestinationInfoDisplay(destinationInfoList: List<DestinationInfo>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "登録地点",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        destinationInfoList.forEach { destInfo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = destInfo.destination.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${destInfo.bearing.toInt()}° / ${formatDistance(destInfo.distance)}",
                    fontSize = 16.sp,
                    color = Color.Blue
                )
            }
        }
    }
}

private fun formatDistance(distance: Float): String {
    return when {
        distance < 1000 -> "${distance.toInt()}m"
        distance < 10000 -> "%.1fkm".format(distance / 1000)
        else -> "${(distance / 1000).toInt()}km"
    }
}

// Map 1σ heading accuracy (deg) to qualitative levels.
// Thresholds can be tuned by UX feedback.
private fun qualityFromSigma(sigmaDeg: Float): String {
    return when {
        sigmaDeg <= 7.5f -> "良"
        sigmaDeg <= 20f -> "中"
        else -> "低"
    }
}
