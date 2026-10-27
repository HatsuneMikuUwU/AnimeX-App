package com.uwu.animex.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedNavIcon(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(selected, spatial) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (selected) {
            scale.snapTo(0.55f)
            rotation.snapTo(-18f)
            launch { rotation.animateTo(0f, spatial) }
            scale.animateTo(1f, spatial)
        } else {
            scale.snapTo(1f)
            rotation.snapTo(0f)
        }
    }

    Box(
        modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = rotation.value
        },
    ) {
        Icon(imageVector = if (selected) selectedIcon else icon, contentDescription = contentDescription)
    }
}
