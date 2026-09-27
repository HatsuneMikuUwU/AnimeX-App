@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.uwu.animex.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.calculateBounds
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.CancellationException

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val msg: String) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

class LoadHandle<T>(val state: UiState<T>, val isRefreshing: Boolean, val refresh: () -> Unit)

private val loadResultCache = HashMap<Any, Any?>()

@Composable
fun <T> rememberLoad(key: Any?, block: suspend (force: Boolean) -> T): LoadHandle<T> {
    val cacheKey = key ?: Unit
    @Suppress("UNCHECKED_CAST")
    val cached = loadResultCache[cacheKey] as? T

    var state by remember(key) {
        mutableStateOf<UiState<T>>(if (cached != null) UiState.Ready(cached) else UiState.Loading)
    }
    var refreshing by remember(key) { mutableStateOf(false) }
    var gen by remember(key) { mutableIntStateOf(0) }

    LaunchedEffect(key, gen) {
        val force = gen > 0
        if (force) refreshing = true else if (cached == null) state = UiState.Loading
        try {
            val result = block(force)
            loadResultCache[cacheKey] = result
            state = UiState.Ready(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = if (cached != null) UiState.Ready(cached) else UiState.Error(e.message ?: "Terjadi kesalahan")
        } finally {
            refreshing = false
        }
    }

    return LoadHandle(state, refreshing) { gen++ }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterLoading() = Box(Modifier.fillMaxSize(), Alignment.Center) {
    LoadingIndicator()
}

@Composable
fun CenterText(text: String, color: Color = Color.Unspecified) =
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { Text(text, color = color) }

/** A [Shape] built from a Material "expressive" [RoundedPolygon] (blob/cookie/clover shapes). */
class RoundedPolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        // Each MaterialShapes polygon has its own bounding box (not always -1..1 on both
        // axes), so measure it and map it to fill the target size exactly instead of assuming
        // fixed bounds — otherwise the shape (and anything clipped inside it) ends up shifted
        // off-center or cropped to a corner.
        val bounds = polygon.calculateBounds()
        val left = bounds[0]
        val top = bounds[1]
        val boundsWidth = (bounds[2] - bounds[0]).takeIf { it > 0f } ?: 1f
        val boundsHeight = (bounds[3] - bounds[1]).takeIf { it > 0f } ?: 1f

        val path = polygon.toPath().asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-left, -top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Named expressive shapes for each empty-state screen, kept behind a stable facade so call
 * sites don't need to opt in to [ExperimentalMaterial3ExpressiveApi] just to reference them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object EmptyShapes {
    val Bookmark: RoundedPolygon = MaterialShapes.Clover4Leaf
    val Genre: RoundedPolygon = MaterialShapes.Clover8Leaf
    val Year: RoundedPolygon = MaterialShapes.Cookie4Sided
    val History: RoundedPolygon = MaterialShapes.Cookie6Sided
    val Today: RoundedPolygon = MaterialShapes.Cookie7Sided
    val Waiting: RoundedPolygon = MaterialShapes.Cookie9Sided
    val MovieList: RoundedPolygon = MaterialShapes.Cookie12Sided
    val Filter: RoundedPolygon = MaterialShapes.Clover4Leaf
    val Player: RoundedPolygon = MaterialShapes.Clover8Leaf
    val Schedule: RoundedPolygon = MaterialShapes.Cookie4Sided
    val Search: RoundedPolygon = MaterialShapes.Cookie6Sided
}

/** Empty-state block: a blob-shaped icon container (Material expressive shape) plus a message. */
@Composable
fun EmptyState(
    message: String,
    icon: ImageVector,
    shape: RoundedPolygon,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .clip(remember(shape) { RoundedPolygonShape(shape) })
                    .background(containerColor),
                Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(96.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                message,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
