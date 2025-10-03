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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.compass_photo),
                contentDescription = "Compass Image",
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(-azimuth)
            )
            // Draw a fixed marker that always points up
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