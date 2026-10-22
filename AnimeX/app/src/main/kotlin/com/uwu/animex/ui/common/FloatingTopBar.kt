package com.uwu.animex.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.ui.theme.barGlass
import com.uwu.animex.ui.theme.resolveGlassStyle
import com.uwu.animex.ui.theme.topScrim
import top.yukonga.miuix.kmp.blur.LayerBackdrop

private val BarHeight = 56.dp
private val BarHorizontalPadding = 16.dp
private val BarTopPadding = 8.dp
private val FadeExtra = 16.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingTopBar(
    backdrop: LayerBackdrop?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String = "",
    onTrailingClick: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val brush = remember(cs.background) { topScrim(cs.background) }
    val container = cs.surfaceContainerHigh
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val glassStyle = resolveGlassStyle(backdrop, appearance.barEffect)

    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(brush, size = Size(size.width, size.height + FadeExtra.toPx()))
            }.statusBarsPadding()
            .padding(horizontal = BarHorizontalPadding)
            .padding(top = BarTopPadding),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingGlassButton(
                contentDescription = "Balik",
                onClick = hapticClick(block = onBack),
                backdrop = backdrop,
                size = BarHeight,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = cs.onSurface)
            }
            if (title != null) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(BarHeight)
                        .barGlass(glassStyle, backdrop, CircleShape, container)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
                            ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (trailingIcon != null) {
                FloatingGlassButton(
                    contentDescription = trailingDescription,
                    onClick = hapticClick(block = onTrailingClick),
                    backdrop = backdrop,
                    size = BarHeight,
                ) {
                    Icon(trailingIcon, contentDescription = null, tint = cs.onSurface)
                }
            }
            if (actions != null) {
                Row(
                    Modifier
                        .height(BarHeight)
                        .barGlass(glassStyle, backdrop, CircleShape, container)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalContentColor provides cs.onSurface) { actions() }
                }
            }
        }
    }
}
