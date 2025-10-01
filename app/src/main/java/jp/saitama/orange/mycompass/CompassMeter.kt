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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

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
        // Direction labels
        DirectionLabels(azimuth = azimuth)

        Spacer(modifier = Modifier.height(8.dp))

        // Meter with vertical lines and destination markers
        CompassMeterBar(
            azimuth = azimuth,
            destinationInfoList = destinationInfoList
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Degree display
        Text(
            text = "${azimuth.toInt()}°",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Display destination info
        if (destinationInfoList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DestinationInfoDisplay(destinationInfoList = destinationInfoList)
        }
    }
}

@Composable
fun DirectionLabels(azimuth: Float) {
    val directions = listOf(
        0f to "N",
        45f to "NE",
        90f to "E",
        135f to "SE",
        180f to "S",
        225f to "SW",
        270f to "W",
        315f to "NW"
    )

    // Find closest direction
    val closest = directions.minByOrNull {
        val diff = abs(it.first - azimuth)
        minOf(diff, 360f - diff)
    }

    Text(
        text = closest?.second ?: "N",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
}

@Composable
fun CompassMeterBar(
    azimuth: Float,
    destinationInfoList: List<DestinationInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f

            // Draw vertical lines for degrees
            // We'll show a range of degrees around the current azimuth
            val degreesRange = 90 // Show 90 degrees on each side
            val pixelsPerDegree = width / (degreesRange * 2)

            for (degree in -degreesRange..degreesRange step 5) {
                val currentDegree = (azimuth + degree + 360) % 360
                val x = centerX + (degree * pixelsPerDegree)

                if (x in 0f..width) {
                    val lineHeight = if (currentDegree.toInt() % 30 == 0) {
                        height * 0.4f
                    } else if (currentDegree.toInt() % 10 == 0) {
                        height * 0.3f
                    } else {
                        height * 0.2f
                    }

                    val color = if (degree == 0) {
                        Color.Red
                    } else {
                        Color.Gray
                    }

                    drawLine(
                        color = color,
                        start = Offset(x, height / 2 - lineHeight / 2),
                        end = Offset(x, height / 2 + lineHeight / 2),
                        strokeWidth = if (degree == 0) 4f else 2f
                    )
                }
            }

            // Draw destination markers
            destinationInfoList.forEach { destInfo ->
                val relativeBearing = destInfo.bearing - azimuth
                val normalizedBearing = when {
                    relativeBearing > 180 -> relativeBearing - 360
                    relativeBearing < -180 -> relativeBearing + 360
                    else -> relativeBearing
                }

                if (normalizedBearing in -degreesRange.toFloat()..degreesRange.toFloat()) {
                    val x = centerX + (normalizedBearing * pixelsPerDegree)

                    // Draw destination marker
                    drawCircle(
                        color = Color.Blue,
                        radius = 8f,
                        center = Offset(x, height * 0.2f)
                    )

                    // Draw line from marker to bottom
                    drawLine(
                        color = Color.Blue,
                        start = Offset(x, height * 0.2f + 8f),
                        end = Offset(x, height * 0.8f),
                        strokeWidth = 2f
                    )
                }
            }

            // Draw center indicator (fixed red line)
            drawLine(
                color = Color.Red,
                start = Offset(centerX, 0f),
                end = Offset(centerX, height),
                strokeWidth = 3f
            )
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