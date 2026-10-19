package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.glassStroke
import com.uwu.animex.ui.theme.topScrim
import top.yukonga.miuix.kmp.blur.LayerBackdrop

private val BarHeight = 56.dp
private val BarHorizontalPadding = 16.dp
private val BarTopPadding = 8.dp
private val FadeExtra = 16.dp

/**
 * Toolbar floating berbentuk kapsul, ukuran dan padding-nya sama dengan search bar di Home.
 * Di belakangnya ada fade ke warna latar supaya konten yang di-scroll memudar mulus.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingTopBar(
    backdrop: LayerBackdrop?,
    onBack: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val brush = remember(cs.background) { topScrim(cs.background) }
    val container = cs.surfaceContainerHigh

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
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .blurEffect(backdrop, shape = CircleShape, blendColor = container)
                .background(backdrop.appBarColor(container), CircleShape)
                .glassStroke()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = hapticClick(block = onBack),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik", tint = cs.onSurface)
            }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
            }
            CompositionLocalProvider(LocalContentColor provides cs.onSurface) { actions() }
        }
    }
}
