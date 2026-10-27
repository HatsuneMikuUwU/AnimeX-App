package com.uwu.animex.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedNavIcon(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "nav-icon-scale",
    )

    Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Icon(imageVector = if (selected) selectedIcon else icon, contentDescription = contentDescription)
    }
}
