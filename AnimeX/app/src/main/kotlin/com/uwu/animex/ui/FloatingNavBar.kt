package com.uwu.animex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Tinggi bottom bar melayang. Layar tab nambahin ini ke bottom padding list-nya
 * biar item terakhir nggak ketutup pill. Default 0 (route tanpa bar).
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Bottom nav model "pill" melayang, gaya Telegram. Kalau hazeState dikasih, background-nya di-blur. */
@Composable
fun FloatingNavBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    val tint = MaterialTheme.colorScheme.surfaceContainer
    val blurModifier = if (hazeState != null) {
        val style = remember(tint) {
            HazeBlurStyle {
                blurRadius(24.dp)
                colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = 0.7f))))
            }
        }
        Modifier.clip(shape).hazeBlur(input = HazeInput.Sources(hazeState), style = style)
    } else {
        Modifier
    }
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = blurModifier,
            shape = shape,
            color = if (hazeState != null) {
                Color.Transparent
            } else {
                tint.copy(alpha = 0.94f)
            },
            shadowElevation = if (hazeState != null) 0.dp else 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        }
    }
}

@Composable
fun RowScope.FloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "navContainer",
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "navTint",
    )
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
