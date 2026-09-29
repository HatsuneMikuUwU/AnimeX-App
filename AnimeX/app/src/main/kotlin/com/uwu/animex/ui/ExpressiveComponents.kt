@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Loading indicator Material Expressive (shape morphing) untuk progress indeterminate. */
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    if (color == Color.Unspecified) {
        LoadingIndicator(modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier, color = color)
    }
}

/** Progress lingkaran bergelombang (wavy) berukuran kecil, untuk status unduhan dsb. */
@Composable
fun SmallWavyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val stroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round)
    }
    CircularWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        stroke = stroke,
        trackStroke = stroke,
    )
}

/** Progress linear bergelombang (wavy). */
@Composable
fun WavyLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/** Pull-to-refresh dengan LoadingIndicator Material Expressive. */
@Composable
fun ExpressivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * Pengganti FilterChip: ToggleButton Material Expressive (bentuk morph bulat -> kotak saat dipilih).
 * Cocok dipakai di LazyRow untuk pilihan tunggal maupun ganda.
 */
@Composable
fun ExpressiveToggleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier,
        colors = ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, fontWeight = FontWeight.Bold)
    }
}

/**
 * Pengganti SuggestionChip/AssistChip: tombol Material Expressive ukuran extra small
 * (bentuk morph saat ditekan). Untuk chip non-toggle seperti genre atau statistik.
 */
@Composable
fun ExpressiveChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, fontWeight = FontWeight.Bold)
    }
}
