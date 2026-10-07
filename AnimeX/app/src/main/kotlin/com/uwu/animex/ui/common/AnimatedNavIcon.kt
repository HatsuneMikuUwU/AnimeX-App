package com.uwu.animex.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

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

    LaunchedEffect(selected) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (selected) {
            scale.snapTo(0.55f)
            rotation.snapTo(-18f)
            val spec = spring<Float>(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium)
            launch { rotation.animateTo(0f, spec) }
            scale.animateTo(1f, spec)
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
