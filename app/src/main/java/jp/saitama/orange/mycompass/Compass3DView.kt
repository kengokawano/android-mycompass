package jp.saitama.orange.mycompass

import android.Manifest
import android.content.Context
import android.util.Log
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
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlin.math.atan2
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
    var smoothedArAzimuth by remember { mutableStateOf(0f) } // AR用の平滑化された方位
    val isArAvailable = remember { checkArAvailability(context) }

    // Camera permission for AR
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // 補間係数
    val lerpFactor = 0.2f // 通常モード用 (追従性UP)
    val arLerpFactor = 0.1f // ARモード用 (プルプル抑制)

    // Request camera permission when AR is enabled
    LaunchedEffect(arEnabled) {
        if (arEnabled && !cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val shouldUseAr = arEnabled && isArAvailable && cameraPermissionState.status.isGranted

    // Create engine, model loader and nodes
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val compassRing = remember { mutableStateOf<Node?>(null) }
    val markerNodes = remember { mutableStateListOf<ModelNode>() }

    // 方角の定義（絶対方位）
    val directions = listOf(
        Triple(0, "N", true), Triple(90, "E", true),
        Triple(180, "S", true), Triple(270, "W", true),
        Triple(45, "NE", false), Triple(135, "SE", false),
        Triple(225, "SW", false), Triple(315, "NW", false)
    )

    val nodes = rememberNodes {
        val ringNode = Node(engine = engine)
        compassRing.value = ringNode
        add(ringNode)
    }

    // Load models asynchronously
    LaunchedEffect(Unit) {
        markerNodes.clear()

        try {
            val modelInstance = modelLoader.createModelInstance(
                assetFileLocation = "models/alphabet_bold.glb"
            )

            // モデルの構造をログ出力
            Log.d("CompassModel", "=== Model Loaded ===")
            Log.d("CompassModel", "Model instance: $modelInstance")

        } catch (e: Exception) {
            Log.e("CompassModel", "Failed to load model", e)
            e.printStackTrace()
        }

        directions.forEach { (degree, label, _) ->
            val radian = Math.toRadians(degree.toDouble())
            val radius = 5f
            val x = (radius * sin(radian)).toFloat()
            val z = -(radius * cos(radian)).toFloat()

            try {
                val modelInstance = modelLoader.createModelInstance(
                    assetFileLocation = "models/alphabet_bold.glb"
                )

                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 0.5f
                ).apply {
                    position = Position(x, 0f, z)
                    rotation = Rotation(0f, degree.toFloat(), 0f)
                }

                compassRing.value?.addChildNode(modelNode)
                markerNodes.add(modelNode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Update rotation and position (with billboard effect)
    val latestAzimuth by rememberUpdatedState(azimuth)
    LaunchedEffect(shouldUseAr) {
        while (true) {
            compassRing.value?.let { ring ->
                if (shouldUseAr) {
                    // ARモード: 方位を平滑化
                    smoothedArAzimuth += (latestAzimuth - smoothedArAzimuth) * arLerpFactor
                    ring.rotation = Rotation(0f, 0f, 0f)

                    markerNodes.forEachIndexed { index, marker ->
                        val absoluteBearing = directions[index].first
                        val relativeBearing = absoluteBearing - smoothedArAzimuth
                        val radian = Math.toRadians(relativeBearing.toDouble())
                        val radius = 4f

                        val x = (radius * sin(radian)).toFloat()
                        val z = -(radius * cos(radian)).toFloat()

                        marker.position = Position(x, 0f, z)

                        // ビルボード: カメラの方を向く
                        val angleToCamera = Math.toDegrees(atan2(x.toDouble(), (-z).toDouble())).toFloat()
                        marker.rotation = Rotation(0f, angleToCamera, 0f)
                    }
                } else {
                    // 通常モード: リング全体を回転（補間あり）
                    val targetRotation = -latestAzimuth
                    currentRotation += (targetRotation - currentRotation) * lerpFactor
                    ring.rotation = Rotation(0f, currentRotation, 0f)

                    // ビルボード: 各マーカーがカメラを向く
                    markerNodes.forEachIndexed { index, marker ->
                        val degree = directions[index].first
                        val radian = Math.toRadians(degree.toDouble())
                        val radius = 5f

                        val x = (radius * sin(radian)).toFloat()
                        val z = -(radius * cos(radian)).toFloat()

                        // 回転後の位置を計算
                        val rotRad = Math.toRadians(currentRotation.toDouble())
                        val rotX = x * cos(rotRad) - z * sin(rotRad)
                        val rotZ = x * sin(rotRad) + z * cos(rotRad)

                        // ビルボード回転
                        val angleToCamera = Math.toDegrees(atan2(rotX, -rotZ)).toFloat()
                        marker.rotation = Rotation(0f, angleToCamera, 0f)
                    }
                }
            }
            kotlinx.coroutines.delay(16) // ~60fps
        }
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
