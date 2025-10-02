package jp.saitama.orange.mycompass

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode

@Composable
fun Compass3DView(
    modifier: Modifier = Modifier,
    azimuth: Float = 0f
) {
    var compassNode by remember { mutableStateOf<CubeNode?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SceneView(context).apply {
                // 仮の立方体ノードを作成（後で矢印に変更）
                val node = CubeNode(
                    engine = engine,
                    size = Position(1f, 2f, 0.5f),
                    center = Position(0f, 0f, 0f)
                ).apply {
                    // ノードの位置
                    position = Position(0f, 0f, -5f)
                }
                compassNode = node
                addChildNode(node)

                // カメラの設定
                cameraNode.position = Position(0f, 2f, 5f)
                cameraNode.lookAt(Position(0f, 0f, 0f))
            }
        },
        update = { view ->
            // 方位角でノードを回転
            compassNode?.rotation = Rotation(0f, azimuth, 0f)
        }
    )
}
