package jp.saitama.orange.mycompass

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sceneview.SceneView
import io.github.sceneview.math.Color as SceneColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Compass3DView(
    modifier: Modifier = Modifier,
    azimuth: Float = 0f
) {
    var compassRing by remember { mutableStateOf<Node?>(null) }
    var currentRotation by remember { mutableStateOf(0f) }

    // 時刻に応じた背景色
    val backgroundColor = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 6..18) {
            Color(0xFF87CEEB) // 昼: 水色
        } else {
            Color(0xFF404040) // 夜: 濃いグレー
        }
    }

    AndroidView(
        modifier = modifier.background(backgroundColor),
        factory = { context ->
            SceneView(context).apply {
                // 背景を透過させてCompose側の色を表示
                setBackgroundColor(AndroidColor.TRANSPARENT)
                scene.skybox = null

                val ringNode = Node(engine = engine)

                val directions = listOf(0, 45, 90, 135, 180, 225, 270, 315)

                directions.forEach { degree ->
                    val radian = Math.toRadians(degree.toDouble())
                    val radius = 5f
                    val x = (radius * sin(radian)).toFloat()
                    val z = -(radius * cos(radian)).toFloat()

                    // オブジェクトの色（白）
                    val marker = CubeNode(
                        engine = engine,
                        size = Position(0.5f, 1.2f, 0.2f),
                        center = Position(0f, 0f, 0f),
                        materialInstance = materialLoader.createColorInstance(SceneColor.WHITE)
                    ).apply {
                        position = Position(x, 0f, z)
                        rotation = Rotation(0f, degree.toFloat(), 0f)
                    }

                    ringNode.addChildNode(marker)
                }

                compassRing = ringNode
                addChildNode(ringNode)

                cameraNode.position = Position(0f, 0f, 0f)
                cameraNode.lookAt(Position(0f, 0f, -1f))
            }
        },
        update = { view ->
            val targetRotation = -azimuth
            currentRotation += (targetRotation - currentRotation) * 0.0001f
            compassRing?.rotation = Rotation(0f, currentRotation, 0f)
        }
    )
}
