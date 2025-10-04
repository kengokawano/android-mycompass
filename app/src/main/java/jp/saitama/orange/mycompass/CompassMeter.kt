package jp.saitama.orange.mycompass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
    modifier: Modifier = Modifier
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
