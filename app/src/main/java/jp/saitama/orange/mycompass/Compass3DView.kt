package jp.saitama.orange.mycompass

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Compass3DView(
    modifier: Modifier = Modifier,
    azimuth: Float = 0f
) {
    var compassRing by remember { mutableStateOf<Node?>(null) }
    var currentRotation by remember { mutableStateOf(0f) }

    // 補間係数（0.1 = ゆっくり、0.3 = 速め）
    val lerpFactor = 0.0001f

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SceneView(context).apply {
                val ringNode = Node(engine = engine)

                // 方角の定義（度数、ラベル、色）
                val directions = listOf(
                    // 主要方角（東西南北）
                    Triple(0, "N", true),      // 北 - 大きい
                    Triple(90, "E", true),     // 東 - 大きい
                    Triple(180, "S", true),    // 南 - 大きい
                    Triple(270, "W", true),    // 西 - 大きい
                    // 副方角（斜め）
                    Triple(45, "NE", false),   // 北東 - 小さい
                    Triple(135, "SE", false),  // 南東 - 小さい
                    Triple(225, "SW", false),  // 南西 - 小さい
                    Triple(315, "NW", false)   // 北西 - 小さい
                )

                directions.forEach { (degree, label, isCardinal) ->
                    val radian = Math.toRadians(degree.toDouble())
                    val radius = 5f

                    val x = (radius * sin(radian)).toFloat()
                    val z = -(radius * cos(radian)).toFloat()

                    // 全て同じサイズ
                    val size = Position(0.5f, 1.2f, 0.2f)

                    val marker = CubeNode(
                        engine = engine,
                        size = size,
                        center = Position(0f, 0f, 0f)
                    ).apply {
                        position = Position(x, 0f, z)
                        rotation = Rotation(0f, degree.toFloat(), 0f)
                    }

                    ringNode.addChildNode(marker)
                }

                compassRing = ringNode
                addChildNode(ringNode)

                // カメラは中心（自分の位置）に配置
                cameraNode.position = Position(0f, 0f, 0f)
                cameraNode.lookAt(Position(0f, 0f, -1f))
            }
        },
        update = { view ->
            // 線形補間で滑らかに回転
            val targetRotation = -azimuth
            currentRotation += (targetRotation - currentRotation) * lerpFactor
            compassRing?.rotation = Rotation(0f, currentRotation, 0f)
        }
    )
}
