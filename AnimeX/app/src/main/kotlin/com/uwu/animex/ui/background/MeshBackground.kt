

package com.uwu.animex.ui.background

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary
    val context        = LocalContext.current

    val parallaxState = rememberParallaxState(
        enableParallax = enableParallax,
        sensitivity = 0.4f,
        context = context
    )

    val meshNodes = remember { generateMeshGrid() }

    val time = rememberAnimatedTime(speedMultiplier)

    val projected = remember { MeshProjection(meshNodes.size) }
    val trianglePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width  = size.width
        val height = size.height
        val tiltX  = parallaxState.tiltX.value
        val tiltY  = parallaxState.tiltY.value
        val strokeWidth = 1.2f * density

        val cameraZ  = 1.6f
        val tiltRad  = Math.toRadians(MESH_GRID_TILT_DEGREES).toFloat()
        val cosTilt  = cos(tiltRad)
        val sinTilt  = sin(tiltRad)

        val t = time.value * PI.toFloat() / 20000f

        meshNodes.forEachIndexed { i, node ->

            val xFreq  = 1.0f + node.baseX * 0.5f
            val yFreq  = 1.2f + node.baseY * 0.6f
            val zFreq  = 0.8f + (node.baseX + node.baseY) * 0.4f
            val xPhase = node.baseX * 2f * PI.toFloat()
            val yPhase = node.baseY * 3f * PI.toFloat()
            val zPhase = (node.baseX + node.baseY) * 1.5f * PI.toFloat()

            val x = node.baseX + node.offsetX * sin(t * xFreq + xPhase)
            val y = node.baseY + node.offsetY * cos(t * yFreq + yPhase)

            val z = node.zAmplitude * sin(t * zFreq + zPhase)

            val normalizedX = (x - 0.5f) * 3.8f
            val normalizedY = (y - 0.5f) * 2.8f

            val rotatedY = normalizedY * cosTilt - z * sinTilt
            val rotatedZ = normalizedY * sinTilt + z * cosTilt
            projected.wx[i] = normalizedX
            projected.wy[i] = rotatedY
            projected.wz[i] = rotatedZ

            val parallaxStrength = node.baseDepth * 60f

            val perspective = cameraZ / (cameraZ - rotatedZ)

            projected.sx[i] = normalizedX * perspective * width  * 0.48f + width  * 0.5f + tiltX * parallaxStrength
            projected.sy[i] = rotatedY    * perspective * height * 0.48f + height * 0.5f + tiltY * parallaxStrength
        }

        val sx = projected.sx; val sy = projected.sy

        meshNodes.forEachIndexed { index, node ->
            val row = index / MESH_COLS
            val col = index % MESH_COLS
            if (row >= MESH_ROWS - 1 || col >= MESH_COLS - 1) return@forEachIndexed
            val color = cellColor(row, col, primaryColor, secondaryColor, tertiaryColor)
            val alpha = 0.14f + node.baseDepth * 0.05f
            fillTriangle(trianglePath, projected, index, index + 1, index + MESH_COLS, color, alpha)
            fillTriangle(trianglePath, projected, index + 1, index + MESH_COLS + 1, index + MESH_COLS, color, alpha)
        }

        meshNodes.forEachIndexed { index, node ->
            val row = index / MESH_COLS
            val col = index % MESH_COLS
            val hasRight = col < MESH_COLS - 1
            val hasBelow = row < MESH_ROWS - 1
            val color = cellColor(row, col, primaryColor, secondaryColor, tertiaryColor)
                .copy(alpha = 0.14f + node.baseDepth * 0.05f)
            val from = Offset(sx[index], sy[index])

            if (hasRight) {
                drawLine(color, from, Offset(sx[index + 1], sy[index + 1]), strokeWidth)
            }
            if (hasBelow) {
                val below = index + MESH_COLS
                drawLine(color, from, Offset(sx[below], sy[below]), strokeWidth)
            }
            if (hasRight && hasBelow) {
                val right = index + 1
                val below = index + MESH_COLS
                drawLine(color, Offset(sx[right], sy[right]), Offset(sx[below], sy[below]), strokeWidth)
            }
        }
    }
}

private fun cellColor(row: Int, col: Int, primary: Color, secondary: Color, tertiary: Color) =
    when ((row + col) % 3) {
        0    -> primary
        1    -> secondary
        else -> tertiary
    }

private const val MESH_LIGHT_X = -0.4f
private const val MESH_LIGHT_Y = -0.6f
private const val MESH_LIGHT_Z = 0.7f

private fun DrawScope.fillTriangle(
    path: Path,
    projected: MeshProjection,
    a: Int, b: Int, c: Int,
    color: Color,
    alpha: Float
) {
    val wx = projected.wx; val wy = projected.wy; val wz = projected.wz
    val e1x = wx[b] - wx[a]; val e1y = wy[b] - wy[a]; val e1z = wz[b] - wz[a]
    val e2x = wx[c] - wx[a]; val e2y = wy[c] - wy[a]; val e2z = wz[c] - wz[a]
    var nx = e1y * e2z - e1z * e2y
    var ny = e1z * e2x - e1x * e2z
    var nz = e1x * e2y - e1y * e2x

    if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
    val light = (nx * MESH_LIGHT_X + ny * MESH_LIGHT_Y + nz * MESH_LIGHT_Z) / len

    val shade = ((light - 0.55f) / 0.4f).coerceIn(0f, 1f)

    path.rewind()
    path.moveTo(projected.sx[a], projected.sy[a])
    path.lineTo(projected.sx[b], projected.sy[b])
    path.lineTo(projected.sx[c], projected.sy[c])
    path.close()
    drawPath(path, color.copy(alpha = alpha * (0.08f + 0.5f * shade)))
}

private class MeshProjection(size: Int) {
    val wx = FloatArray(size)
    val wy = FloatArray(size)
    val wz = FloatArray(size)
    val sx = FloatArray(size)
    val sy = FloatArray(size)
}

private const val MESH_ROWS = 12
private const val MESH_COLS = 12
private const val MESH_GRID_TILT_DEGREES = 10.0

private fun generateMeshGrid(): List<MeshNode> {
    val nodes = mutableListOf<MeshNode>()

    for (row in 0 until MESH_ROWS) {
        for (col in 0 until MESH_COLS) {
            val baseX = col / (MESH_COLS - 1f)
            val baseY = row / (MESH_ROWS - 1f)

            val offsetX    = (Random.nextFloat() - 0.5f) * 0.04f
            val offsetY    = (Random.nextFloat() - 0.5f) * 0.04f
            val zAmplitude = Random.nextFloat() * 0.15f

            val centerDistX = (baseX - 0.5f) * 2f
            val centerDistY = (baseY - 0.5f) * 2f
            val baseDepth   = sqrt(centerDistX * centerDistX + centerDistY * centerDistY) / sqrt(2f)

            nodes.add(MeshNode(
                baseX      = baseX,
                baseY      = baseY,
                offsetX    = offsetX,
                offsetY    = offsetY,
                baseDepth  = baseDepth,
                zAmplitude = zAmplitude
            ))
        }
    }

    return nodes
}

private data class MeshNode(
    val baseX: Float,
    val baseY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val baseDepth: Float,
    val zAmplitude: Float
)
