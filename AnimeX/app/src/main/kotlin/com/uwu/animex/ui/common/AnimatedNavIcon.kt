package com.uwu.animex.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.MotionChoice

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedNavIcon(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val animated = settings.motionScheme == MotionChoice.EXPRESSIVE
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec =
            if (animated) {
                MaterialTheme.motionScheme.defaultSpatialSpec()
            } else {
                snap()
            },
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
