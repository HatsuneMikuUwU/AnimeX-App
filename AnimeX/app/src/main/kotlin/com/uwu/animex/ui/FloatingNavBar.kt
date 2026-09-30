package com.uwu.animex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.uwu.animex.ui.liquid.InteractiveHighlight

/**
 * Tinggi bottom bar melayang. Layar tab nambahin ini ke bottom padding list-nya
 * biar item terakhir nggak ketutup pill. Default 0 (route tanpa bar).
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Bottom nav "pill" dengan liquid glass (Kyant Backdrop) yang menyatu Material 3.
 *
 * [backdrop] dari [com.kyant.backdrop.backdrops.rememberLayerBackdrop] di parent,
 * content area di-tag dengan [com.kyant.backdrop.backdrops.layerBackdrop].
 */
@Composable
fun FloatingNavBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    tabsCount: Int,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    // Surface container M3 → glass tetap ikut dynamic color / light-dark
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val containerColor = surface.copy(alpha = if (isDark) 0.45f else 0.55f)
    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
    val highlightAlpha = if (isDark) 0.35f else 0.55f
    val scope = rememberCoroutineScope()
    val interactiveHighlight = remember(scope) {
        InteractiveHighlight(animationScope = scope)
    }
    // Keep API params referenced so callers (selectedTabIndex / tabs) stay in sync
    @Suppress("UNUSED_EXPRESSION")
    selectedTabIndex()
    @Suppress("UNUSED_PARAMETER")
    val _tabs = tabsCount
    @Suppress("UNUSED_PARAMETER")
    val _onSel = onTabSelected

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        // Liquid glass: vibrancy + soft blur + edge lens
                        vibrancy()
                        blur(12f.dp.toPx())
                        lens(20f.dp.toPx(), 28f.dp.toPx())
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = highlightAlpha)
                    },
                    onDrawSurface = {
                        // Tint surface M3 supaya readable & menyatu tema
                        drawRect(containerColor)
                        // Sedikit primary hue biar terasa Material, bukan pure iOS glass
                        drawRect(primaryTint, blendMode = BlendMode.Hue)
                    },
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .padding(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * Overload sederhana (tanpa tab index / backdrop) — fallback solid surfaceContainer.
 * Tetap dipakai kalau parent belum pasang layer backdrop.
 */
@Composable
fun FloatingNavBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    val tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(tint)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
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
