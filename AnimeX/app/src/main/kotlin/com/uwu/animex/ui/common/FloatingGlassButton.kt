package com.uwu.animex.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.ui.theme.barGlass
import com.uwu.animex.ui.theme.resolveGlassStyle
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun FloatingGlassButton(
    contentDescription: String,
    onClick: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    size: Dp = FloatingTabBarHeight,
    content: @Composable () -> Unit,
) {
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val glassStyle = resolveGlassStyle(backdrop, appearance.barEffect)
    Box(
        modifier =
            modifier
                .size(size)
                .barGlass(glassStyle, backdrop, CircleShape, MaterialTheme.colorScheme.surfaceContainerHigh)
                .clip(CircleShape)
                .semantics { this.contentDescription = contentDescription }
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
