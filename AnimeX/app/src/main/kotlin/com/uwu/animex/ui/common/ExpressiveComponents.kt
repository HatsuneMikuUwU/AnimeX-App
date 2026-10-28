@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val accessibleModifier = modifier.clearAndSetSemantics { contentDescription = "Sedang memuat" }
    if (color == Color.Unspecified) {
        LoadingIndicator(modifier = accessibleModifier)
    } else {
        LoadingIndicator(modifier = accessibleModifier, color = color)
    }
}

@Composable
fun SmallWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stroke =
        remember(density) {
            Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round)
        }
    CircularWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        stroke = stroke,
        trackStroke = stroke,
    )
}

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

@Composable
fun ExpressivePullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errors: kotlinx.coroutines.flow.Flow<String>? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (errors != null) {
        androidx.compose.runtime.LaunchedEffect(errors) {
            errors.collect { msg ->
                android.widget.Toast
                    .makeText(context, msg, android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
    val state = rememberPullToRefreshState()
    val hapticOnRefresh = hapticClick(block = onRefresh)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = hapticOnRefresh,
        modifier = modifier,
        enabled = enabled,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = LocalTopInset.current),
            )
        },
        content = content,
    )
}

@Composable
fun ExpressiveToggleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val hapticOnClick = hapticToggle(checked = selected, block = onClick)
    ToggleButton(
        checked = selected,
        onCheckedChange = { hapticOnClick() },
        modifier = modifier,
        colors =
            ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            CountBadge(
                count = count,
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
            )
        }
    }
}

@Composable
fun AppBadge(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .background(cs.surfaceContainerHigh, CircleShape)
                .padding(BadgeRingWidth)
                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .background(cs.primaryContainer, CircleShape)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides cs.onPrimaryContainer) {
            ProvideTextStyle(
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                content()
            }
        }
    }
}

private val BadgeRingWidth = 1.33.dp

@Composable
fun AppBadgedBox(
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var shown by remember { mutableIntStateOf(count) }
    if (count > 0) shown = count
    val scale by animateFloatAsState(
        targetValue = if (count > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "badge-scale",
    )
    BadgedBox(
        modifier = modifier,
        badge = {
            if (scale > 0f) {
                AppBadge(
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                ) {
                    Text(if (shown > 99) "99+" else shown.toString(), maxLines = 1)
                }
            }
        },
        content = content,
    )
}

@Composable
fun CountBadge(
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                .clip(CircleShape)
                .background(containerColor)
                .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ExpressiveChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leading: (@Composable () -> Unit)? = null,
    hapticEnabled: Boolean = true,
) {
    Button(
        onClick = if (hapticEnabled) hapticClick(block = onClick) else onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.buttonColors(
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

@Composable
fun RotatingCookieFrame(
    modifier: Modifier = Modifier,
    frameSize: Dp = 176.dp,
    innerSize: Dp = 116.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val spin = rememberInfiniteTransition(label = "cookie-spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "cookie-angle",
    )
    Box(modifier.size(frameSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle }
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(cs.primaryContainer),
        )
        Box(
            Modifier
                .size(innerSize)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(cs.primary),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
fun AnimatedEmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "empty-state")
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "spin",
    )
    val breathe by t.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val bob by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val bobPx = with(LocalDensity.current) { 4.dp.toPx() }

    Box(
        modifier.fillMaxSize().padding(top = LocalTopInset.current, bottom = LocalBottomInset.current).padding(24.dp),
        Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 200.dp, height = 190.dp)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(150.dp)
                        .graphicsLayer {
                            rotationZ = spin
                            scaleX = breathe
                            scaleY = breathe
                        }.clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(cs.primaryContainer),
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp), tint = cs.onPrimary)
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(54.dp)
                        .graphicsLayer {
                            rotationZ = -spin * 0.8f
                            translationY = bob * bobPx
                        }.clip(MaterialShapes.Clover4Leaf.toShape())
                        .background(cs.primary),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .size(36.dp)
                        .graphicsLayer {
                            rotationZ = spin * 1.2f
                            translationY = -bob * bobPx
                        }.clip(MaterialShapes.Cookie6Sided.toShape())
                        .background(cs.secondary),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp)
                        .size(22.dp)
                        .graphicsLayer {
                            rotationZ = -spin * 1.6f
                            translationY = bob * bobPx
                        }.clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(cs.tertiary),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    modifier = Modifier.widthIn(max = 280.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
