package jp.saitama.orange.mycompass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private val DestinationFlagColor = Color(0xFFFF9800)
private val DestinationHighlightColor = Color(0xFFFFF59D)
private val DestinationAccentColor = Color(0xFFFF9800)

@Composable
fun CompassMeter(
    azimuth: Float,
    destinationInfoList: List<DestinationInfo> = emptyList(),
    modifier: Modifier = Modifier,
    pitch: Float = 0f,
    roll: Float = 0f,
    headingAccuracyDeg: Float? = null,
    sensorAccuracy: Int? = null,
    useTrueNorth: Boolean = false,
    declinationDeg: Float = 0f,
    distanceUnit: String = "meter",
    strideLengthCm: Int = 70,
    compassType: Int = 0
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Effective heading for display text
        val displayAzimuth = if (useTrueNorth) {
            ((azimuth + declinationDeg + 360f) % 360f)
        } else azimuth

        Text(
            text = "${displayAzimuth.toInt()}°",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Fixed upward triangle (Device front direction)
        Spacer(modifier = Modifier.height(6.dp))
        FixedUpTriangle(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
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

            // The dial is rotated by displayAzimuth so that "North" on the dial
            // points to actual North relative to the device.
            val dialRotation = -displayAzimuth

            // Rotating container for the compass image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(dialRotation)
            ) {
                val compassImageRes = if (compassType == 1) R.drawable.compass02 else R.drawable.compass01
                Image(
                    painter = painterResource(id = compassImageRes),
                    contentDescription = "Compass Image",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay rotated with the dial
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(dialRotation)
            ) {
                if (compassType == 0) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius = size.width / 2

                        // North marker (red) on the dial
                        drawLine(
                            color = Color.Red,
                            start = Offset(x = centerX, y = 0f),
                            end = Offset(x = centerX, y = size.height * 0.15f),
                            strokeWidth = 8f
                        )

                        destinationInfoList.forEach { destInfo ->
                            // DestInfo.bearing is True Bearing.
                            // Since the dial is already adjusted to North (True or Magnetic),
                            // we need to place the marker at its bearing relative to True North.
                            // If the dial is Magnetic, we subtract declination to align it.
                            val drawBearing = if (useTrueNorth) {
                                destInfo.bearing
                            } else {
                                // Convert True Bearing to Magnetic Bearing for the magnetic dial
                                ((destInfo.bearing - declinationDeg + 360f) % 360f)
                            }
                            
                            val angleRad = Math.toRadians(drawBearing.toDouble() - 90)

                            val outerX = centerX + radius * cos(angleRad).toFloat()
                            val outerY = centerY + radius * sin(angleRad).toFloat()
                            val innerX = centerX + (radius - size.width * 0.15f) * cos(angleRad).toFloat()
                            val innerY = centerY + (radius - size.height * 0.15f) * sin(angleRad).toFloat()

                            drawLine(
                                color = DestinationAccentColor,
                                start = Offset(x = outerX, y = outerY),
                                end = Offset(x = innerX, y = innerY),
                                strokeWidth = 8f
                            )
                        }
                    }
                }

                destinationInfoList.forEach { destInfo ->
                    val drawBearing = if (useTrueNorth) {
                        destInfo.bearing
                    } else {
                        ((destInfo.bearing - declinationDeg + 360f) % 360f)
                    }

                    val angleRad = Math.toRadians(drawBearing.toDouble() - 90)
                    val centerX = boxSizePx / 2f
                    val centerY = boxSizePx / 2f

                    val margin = 16 * density
                    val flagRadius = (boxSizePx / 2f) + margin

                    val flagXPx = centerX + flagRadius * cos(angleRad).toFloat()
                    val flagYPx = centerY + flagRadius * sin(angleRad).toFloat()

                    val iconHalfPx = (12 * density).roundToInt()

                    Column(
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val x = (flagXPx - placeable.width / 2f).roundToInt()
                                val y = (flagYPx - iconHalfPx).roundToInt()
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeRelative(x, y)
                                }
                            }
                            .graphicsLayer {
                                // Keep the flag/text upright relative to the screen
                                rotationZ = -dialRotation
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = destInfo.destination.name,
                            tint = DestinationFlagColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = destInfo.destination.name,
                            fontSize = 10.sp,
                            color = DestinationAccentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tilt indicator (stays fixed relative to device)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                val sizeDp = 72.dp
                val strokeWidth = 2.dp
                Box(
                    modifier = Modifier.padding(12.dp).size(sizeDp)
                ) {
                    val tiltDotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.25f),
                            radius = r,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx())
                        )
                        val maxAngle = 45f
                        val nx = (roll / maxAngle).coerceIn(-1f, 1f)
                        val ny = (-pitch / maxAngle).coerceIn(-1f, 1f)
                        var dx = nx * r
                        var dy = ny * r
                        val len = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (len > r) {
                            val scale = r / len
                            dx *= scale
                            dy *= scale
                        }
                        drawCircle(
                            color = tiltDotColor,
                            radius = r * 0.08f,
                            center = Offset(center.x + dx, center.y + dy)
                        )
                    }
                }
            }
        }

        if (destinationInfoList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            DestinationInfoDisplay(
                destinationInfoList = destinationInfoList,
                useTrueNorth = useTrueNorth,
                declinationDeg = declinationDeg,
                distanceUnit = distanceUnit,
                strideLengthCm = strideLengthCm
            )
        }
    }
}

@Composable
fun DestinationInfoDisplay(
    destinationInfoList: List<DestinationInfo>,
    useTrueNorth: Boolean = false,
    declinationDeg: Float = 0f,
    distanceUnit: String = "meter",
    strideLengthCm: Int = 70
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.compass_registered_destinations),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DestinationAccentColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(destinationInfoList) { destInfo ->
                val bearingDisplay = if (useTrueNorth) {
                    destInfo.bearing
                } else {
                    ((destInfo.bearing - declinationDeg + 360f) % 360f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DestinationHighlightColor, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = destInfo.destination.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = DestinationAccentColor,
                        modifier = Modifier.weight(1f)
                    )
                    val distanceStr = formatDistance(destInfo.distance, distanceUnit)
                    val displayText = if (destInfo.distance >= 30f) {
                        val steps = (destInfo.distance / (strideLengthCm / 100f)).toInt()
                        val stepsFormatted = String.format("%,d", steps)
                        stringResource(R.string.compass_bearing_distance_with_steps, bearingDisplay.toInt(), distanceStr, stepsFormatted)
                    } else {
                        "${bearingDisplay.toInt()}° / $distanceStr"
                    }
                    Text(
                        text = displayText,
                        fontSize = 16.sp,
                        color = DestinationAccentColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 120.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedUpTriangle(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2f, 0f)
            lineTo(0f, h)
            lineTo(w, h)
            close()
        }
        drawPath(path = path, color = color)
    }
}

private fun formatDistance(distanceMeters: Float, unit: String): String {
    return if (unit == "mile") {
        val miles = distanceMeters / 1609.344f
        when {
            miles < 0.1f -> "${(miles * 5280f).toInt()}ft"
            miles < 10f -> "%.2fmi".format(miles)
            else -> "${miles.toInt()}mi"
        }
    } else {
        when {
            distanceMeters < 1000 -> "${distanceMeters.toInt()}m"
            distanceMeters < 10000 -> "%.1fkm".format(distanceMeters / 1000f)
            else -> "${(distanceMeters / 1000f).toInt()}km"
        }
    }
}
