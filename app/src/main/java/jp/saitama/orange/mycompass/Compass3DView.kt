package jp.saitama.orange.mycompass

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.ar.core.ArCoreApk
import io.github.sceneview.Scene
import io.github.sceneview.ar.ARScene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberNodes
import kotlin.math.cos
import kotlin.math.sin

fun checkArAvailability(context: Context): Boolean {
    return try {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        availability.isSupported
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Compass3DView(
    modifier: Modifier = Modifier,
    azimuth: Float = 0f,
    arEnabled: Boolean = false
) {
    val context = LocalContext.current
    var currentRotation by remember { mutableStateOf(0f) }
    val isArAvailable = remember { checkArAvailability(context) }

    // Camera permission for AR
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // 補間係数
    val lerpFactor = 0.05f

    // Request camera permission when AR is enabled
    LaunchedEffect(arEnabled) {
        if (arEnabled && !cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val shouldUseAr = arEnabled && isArAvailable && cameraPermissionState.status.isGranted

    // Create engine and nodes
    val engine = rememberEngine()
    val compassRing = remember { mutableStateOf<Node?>(null) }

    val nodes = rememberNodes {
        val ringNode = Node(engine = engine)

        // 方角の定義
        val directions = listOf(
            Triple(0, "N", true), Triple(90, "E", true),
            Triple(180, "S", true), Triple(270, "W", true),
            Triple(45, "NE", false), Triple(135, "SE", false),
            Triple(225, "SW", false), Triple(315, "NW", false)
        )

        directions.forEach { (degree, _, _) ->
            val radian = Math.toRadians(degree.toDouble())
            val radius = 5f
            val x = (radius * sin(radian)).toFloat()
            val z = -(radius * cos(radian)).toFloat()

            val marker = CubeNode(
                engine = engine,
                size = Position(0.5f, 1.2f, 0.2f),
                center = Position(0f, 0f, 0f)
            ).apply {
                position = Position(x, 0f, z)
                rotation = Rotation(0f, degree.toFloat(), 0f)
            }
            ringNode.addChildNode(marker)
        }

        compassRing.value = ringNode
        add(ringNode)
    }

    // Update rotation
    LaunchedEffect(azimuth) {
        val targetRotation = -azimuth
        currentRotation += (targetRotation - currentRotation) * lerpFactor
        compassRing.value?.rotation = Rotation(0f, currentRotation, 0f)
    }

    Column(modifier = modifier) {
        if (arEnabled && !isArAvailable) {
            Text(
                text = "ARCore is not available on this device.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (shouldUseAr) {
            ARScene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                childNodes = nodes
            )
        } else {
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                childNodes = nodes
            )
        }
    }
}
