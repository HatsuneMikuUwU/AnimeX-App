@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick
import java.text.NumberFormat
import java.util.Locale

internal val EpisodePrefix = Regex("^Episode\\s*", RegexOption.IGNORE_CASE)

private val numFmt: NumberFormat by lazy {
    NumberFormat.getIntegerInstance(
        Locale
            .Builder()
            .setLanguage("id")
            .setRegion("ID")
            .build(),
    )
}

@Composable
fun fmtNum(s: String?): String {
    val n = s?.toLongOrNull() ?: return s.orEmpty()
    val compact = Appearance.settings
        .collectAsStateWithLifecycle()
        .value.compactNumbers
    return when {
        !compact || n < 1_000L -> numFmt.format(n)
        n < 1_000_000L -> compactNum(n / 100L, "k")
        else -> compactNum(n / 100_000L, "M")
    }
}

private fun compactNum(
    tenths: Long,
    suffix: String,
): String {
    val whole = tenths / 10
    val frac = tenths % 10
    return if (frac == 0L) "$whole$suffix" else "$whole,$frac$suffix"
}

fun Movie.coverOrPoster(): String? = image_cover?.takeIf { it.isNotBlank() } ?: image_poster?.takeIf { it.isNotBlank() }

fun Movie.label(): String? =
    episode_index?.takeIf { it.isNotBlank() }?.let { "Ep $it" } ?: genre?.takeIf { it.isNotBlank() }

@Composable
fun Poster(
    url: String?,
    modifier: Modifier,
    radius: Dp = 20.dp,
    keepPrevious: Boolean = false,
) {
    val ctx = LocalPlatformContext.current
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var lastPainter by remember { mutableStateOf<Painter?>(null) }
    val base = Api.baseUrl
    val data = remember(url, base) { Api.absUrl(url) }
    val request =
        remember(data, attempt, ctx) {
            ImageRequest
                .Builder(ctx)
                .data(data)
                .crossfade(AppMotion.IMAGE_CROSSFADE_MS)
                .apply { if (attempt > 0) memoryCacheKeyExtra("retry", attempt.toString()) }
                .build()
        }
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (data != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = if (keepPrevious) lastPainter else null,
                onSuccess = {
                    failed = false
                    if (keepPrevious) lastPainter = it.painter
                },
                onError = { failed = true },
            )
        }
        if (failed && data != null) {
            RetryOnReconnect {
                failed = false
                attempt++
            }
            Icon(
                Icons.Outlined.BrokenImage,
                contentDescription = "Gagal memuat gambar. Ketuk untuk mencoba lagi",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clickable(
                        onClick = hapticClick {
                            failed = false
                            attempt++
                        },
                    ).padding(10.dp),
            )
        }
    }
}

@Composable
private fun RetryOnReconnect(onRetry: () -> Unit) {
    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(onRetry)
    var sawOffline by remember { mutableStateOf(false) }
    LaunchedEffect(online) {
        if (!online) {
            sawOffline = true
        } else if (sawOffline) {
            latest()
        }
    }
}

@Composable
fun Modifier.cardBackground(): Modifier = background(MaterialTheme.colorScheme.surfaceContainer)

@Composable
internal fun OverlayChip(
    icon: ImageVector,
    text: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(
                start = if (compact) 6.dp else 7.dp,
                end = if (compact) 8.dp else 9.dp,
                top = 3.dp,
                bottom = 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(if (compact) 12.dp else 14.dp), tint = iconTint)
        Text(
            text,
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal fun List<Movie>.distinctById(): List<Movie> = distinctBy { it.id ?: Any() }

internal fun Movie.listKey(): Any = id ?: System.identityHashCode(this)

@Composable
fun SectionHeader(
    title: String,
    onMore: (() -> Unit)?,
    topPadding: Dp = 18.dp,
    icon: ImageVector? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val spin = rememberInfiniteTransition(label = "header-spin")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
                label = "header-angle",
            )
            Box(Modifier.size(44.dp), Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = angle }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onMore != null) {
            FilledTonalIconButton(onClick = hapticClick(block = onMore), shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Lihat semuanya")
            }
        }
    }
}

@Composable
internal fun StatLine(
    badge: @Composable () -> Unit,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        badge()
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            lineHeight = lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ViewsBadge() =
    Icon(
        Icons.Outlined.PlayCircleOutline,
        null,
        Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.error,
    )

@Composable
internal fun FavoriteBadge() =
    Icon(
        Icons.Outlined.Favorite,
        null,
        Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.tertiary,
    )

@Composable
internal fun ScheduleBadge() =
    Icon(
        Icons.Outlined.Schedule,
        null,
        Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.secondary,
    )

@Composable
internal fun CalendarBadge() =
    Icon(
        Icons.Outlined.CalendarMonth,
        null,
        Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.secondary,
    )
