@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.uwu.animex.core.image.DominantColor
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.viewmodel.PagedListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

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
                .crossfade(160)
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
fun Modifier.cardBackground(): Modifier = background(MaterialTheme.colorScheme.surfaceContainerHigh)

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

private fun List<Movie>.distinctById(): List<Movie> = distinctBy { it.id ?: Any() }

private fun Movie.listKey(): Any = id ?: System.identityHashCode(this)

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
internal fun StarBadge() =
    Icon(
        Icons.Outlined.Star,
        null,
        Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.tertiary,
    )

@Composable
private fun CalendarBadge() =
    Icon(
        Icons.Outlined.CalendarMonth,
        null,
        Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.secondary,
    )

@Composable
private fun rememberPrefetchOnClick(
    posterUrl: String?,
    onClick: () -> Unit,
): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onClick)
    return remember(posterUrl) {
        {
            if (Appearance.settings.value.coverTheme) {
                Api.absUrl(posterUrl)?.let { DominantColor.prefetch(ctx, it) }
            }
            latest()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitCard(
    m: Movie,
    modifier: Modifier,
    showTime: Boolean = false,
    showReleaseDate: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    labelOverride: String? = null,
    genreAsChip: Boolean = false,
    similarityPercent: Int? = null,
    onClick: () -> Unit,
) {
    val click = rememberPrefetchOnClick(m.image_poster, onClick)
    val statusLongClick = rememberStatusLongClick(m)
    val chipLabel =
        labelOverride?.takeIf { it.isNotBlank() }
            ?: m.episode_index?.takeIf { it.isNotBlank() }?.let { "Ep $it" }
    val time = if (showTime) m.time?.takeIf { it.isNotBlank() } else null
    val genre = m.genre?.takeIf { it.isNotBlank() }
    val chipFromGenre = if (genreAsChip) genre?.replace(Regex("^Episode\\s*", RegexOption.IGNORE_CASE), "Ep ") else null
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = onLongClick ?: statusLongClick, onClick = click)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Poster(m.image_poster, Modifier.fillMaxSize(), radius = 12.dp)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    ),
            )
            val chipText =
                similarityPercent?.let { if (it >= 100) "Saran" else "$it%" } ?: time ?: chipFromGenre ?: chipLabel.takeIf { !showTime }
            if (chipText != null) {
                Text(
                    chipText,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OverlayChip(
                Icons.Outlined.Star,
                fmtNum(m.favorites),
                MaterialTheme.colorScheme.tertiary,
                Modifier.align(Alignment.BottomEnd).padding(6.dp),
                compact = true,
            )
        }
        if (chipFromGenre == null) {
            Text(
                genre ?: " ",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            m.title.orEmpty(),
            modifier = Modifier.padding(top = if (chipFromGenre == null) 2.dp else 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(4.dp))
        Box {
            when {
                showReleaseDate -> {
                    val releaseLabel = releaseDateLabel(m)
                    StatLine(
                        { CalendarBadge() },
                        if (releaseLabel.isNullOrBlank()) "TBA" else releaseLabel,
                        MaterialTheme.colorScheme.secondary,
                        lineHeight = 14.sp,
                    )
                }
                else ->
                    StatLine(
                        { ViewsBadge() },
                        "${fmtNum(m.views)} dilihat",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp,
                    )
            }
        }
    }
}

internal fun releaseDateLabel(m: Movie): String? = formatDateWithUnknown(m.aired_start) ?: formatDateWithUnknown(m.year)

internal fun formatDateWithUnknown(rawDate: String?): String? {
    val raw = rawDate.usableDate() ?: return null
    val parts = raw.take(10).split('-')
    val y =
        parts.getOrNull(0)?.takeIf { it.length == 4 && it.all(Char::isDigit) && it != "0000" }
            ?: return raw.take(10)

    fun part(i: Int): String =
        parts.getOrNull(i)?.takeIf { it.length == 2 && it.all(Char::isDigit) && it.any { c -> c != '0' } } ?: "??"
    return "$y-${part(1)}-${part(2)}"
}

internal fun releaseYearLabel(m: Movie): String =
    (m.year.usableDate() ?: m.aired_start.usableDate())
        ?.take(4)
        ?.takeIf { it.length == 4 && it.all(Char::isDigit) && it != "0000" }
        ?: "????"

private fun String?.usableDate(): String? {
    val v = this?.trim().orEmpty()
    if (v.isEmpty()) return null
    return when (v.lowercase()) {
        "unknown", "null", "n/a", "-", "tba", "?" -> null
        else -> v
    }
}

@Composable
private fun rememberHomeCardScale(): Float {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    return settings.homeCardScale
}

@Composable
fun PortraitRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    showTime: Boolean = false,
    genreAsChip: Boolean = false,
) {
    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val unique = list.distinctById()
        items(unique, key = { it.listKey() }, contentType = { "portrait" }) { m ->
            PortraitCard(m, Modifier.width(105.dp * scale), showTime, genreAsChip = genreAsChip) { m.id?.let(onOpen) }
        }
    }
}

/** Portrait row whose cards get an extra "xx% mirip" chip (same style as the "new !!" chip). */
@Composable
fun SimilarPortraitRow(
    list: List<SimilarMovie>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = rememberHomeCardScale()
    LazyRow(modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.distinctBy { it.movie.id ?: Any() }, key = { it.movie.listKey() }, contentType = { "similar" }) { sm ->
            PortraitCard(sm.movie, Modifier.width(105.dp * scale), similarityPercent = sm.percent) {
                sm.movie.id?.let(onOpen)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTitleRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.distinctById().chunked(2), key = { it.first().listKey() }, contentType = { "newTitle" }) { pair ->
            Column(Modifier.width(280.dp * scale), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { m ->
                    val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
                    val statusLongClick = rememberStatusLongClick(m)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .cardBackground()
                            .hapticClick(onLongClick = statusLongClick, onClick = click)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Poster(m.image_poster, Modifier.size(70.dp * scale, 99.dp * scale), 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(
                                m.label().orEmpty(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                m.title.orEmpty(),
                                modifier = Modifier.padding(top = 2.dp),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatLine(
                                    { ViewsBadge() },
                                    "${fmtNum(m.views)} dilihat",
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp,
                                )
                                StatLine(
                                    { StarBadge() },
                                    "${fmtNum(m.favorites)} favorit",
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaitingRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.distinctById(), key = { it.listKey() }, contentType = { "waiting" }) { m ->
            val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
            val statusLongClick = rememberStatusLongClick(m)
            Column(
                Modifier
                    .width(280.dp * scale)
                    .clip(RoundedCornerShape(24.dp))
                    .cardBackground()
                    .hapticClick(onLongClick = statusLongClick, onClick = click)
                    .padding(8.dp),
            ) {
                Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                    Poster(
                        m.coverOrPoster(),
                        Modifier.fillMaxWidth().height(118.dp * scale),
                        radius = 16.dp,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                ),
                            ),
                    )
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val releaseLabel = releaseDateLabel(m)
                        if (!releaseLabel.isNullOrBlank()) {
                            OverlayChip(Icons.Outlined.CalendarMonth, releaseLabel, MaterialTheme.colorScheme.secondary)
                        }
                        OverlayChip(Icons.Outlined.Star, fmtNum(m.favorites), MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(
                    m.genre?.takeIf { it.isNotBlank() } ?: " ",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                )
                Text(
                    m.title.orEmpty(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
                )
                Text(
                    m.synopsis.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressPosterCard(
    posterUrl: String?,
    title: String,
    watched: Int,
    total: Int,
    modifier: Modifier = Modifier,
    rating: Int? = null,
    loading: Boolean = false,
    label: String? = null,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val click = rememberPrefetchOnClick(posterUrl, onClick)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = onLongClick, onClick = click)
            .padding(8.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(12.dp))) {
            Poster(posterUrl, Modifier.fillMaxWidth().height(150.dp), radius = 12.dp)
            if (rating != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            ),
                        ),
                )
                OverlayChip(
                    Icons.Outlined.Star,
                    "$rating",
                    MaterialTheme.colorScheme.tertiary,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                )
            }
        }
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        val infoAlpha by androidx.compose.animation.core.animateFloatAsState(
            if (loading) 0f else 1f,
            label = "progressInfoAlpha",
        )
        if (label != null) {
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            WavyLinearProgress(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            Text(
                if (total > 0) "$watched/$total Ep" else "$watched/- Ep",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.graphicsLayer { alpha = infoAlpha },
            )
            WavyLinearProgress(
                progress = { if (total > 0 && !loading) (watched.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).graphicsLayer { alpha = infoAlpha },
            )
        }
    }
}

val WatchStatus.icon: ImageVector
    get() =
        when (this) {
            WatchStatus.WATCHING -> Icons.Outlined.PlayCircleOutline
            WatchStatus.COMPLETED -> Icons.Outlined.CheckCircleOutline
            WatchStatus.ON_HOLD -> Icons.Outlined.PauseCircleOutline
            WatchStatus.DROPPED -> Icons.Outlined.DeleteOutline
            WatchStatus.PLAN_TO_WATCH -> Icons.Outlined.Schedule
        }

@Composable
fun LocalProgressCard(
    m: Movie,
    status: WatchStatus?,
    modifier: Modifier = Modifier,
    refreshTick: Int = 0,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val statusLongClick = rememberStatusLongClick(m)
    val history by History.items.collectAsStateWithLifecycle()
    val last = remember(history, m.id) { history.firstOrNull { it.id == m.id } }
    val watch by remember(last?.episode_id) { Progress.watchFlow(last?.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(last?.episode_id))
    val epNum = last?.episode_index?.toIntOrNull()
    val totalOrNull = rememberTotalEpisodes(m.id, refreshTick)
    val total = totalOrNull ?: 0
    val watches by Progress.watches.collectAsStateWithLifecycle()
    val doneCount =
        remember(watches, totalOrNull, m.id) {
            val ids = m.id?.let { TotalEpisodesCache[it]?.episodeIds }.orEmpty()
            ids.count { Progress.isDoneWatch(watches[it]) }
        }
    val fromHistory =
        when {
            epNum == null -> 0
            Progress.isDoneWatch(watch) -> epNum
            else -> (epNum - 1).coerceAtLeast(0)
        }
    var watched = maxOf(fromHistory, doneCount)
    if (status == WatchStatus.COMPLETED && total > 0) watched = total
    if (total > 0) watched = watched.coerceAtMost(total)
    ProgressPosterCard(
        posterUrl = m.image_poster,
        title = m.title.orEmpty(),
        watched = watched,
        total = total,
        modifier = modifier,
        loading = totalOrNull == null,
        onLongClick = onLongClick ?: statusLongClick,
        onClick = onClick,
    )
}

@Composable
fun rememberTotalEpisodes(
    movieId: String?,
    refreshTick: Int = 0,
): Int? {
    val state by produceState<Int?>(movieId?.let { TotalEpisodesCache[it]?.total }, movieId, refreshTick) {
        if (movieId == null) {
            value = 0
            return@produceState
        }
        val total = fetchTotalEpisodes(movieId)
        value = if (total > 0) total else (value ?: 0)
    }
    return state
}

private suspend fun fetchTotalEpisodes(movieId: String): Int {
    val cached = TotalEpisodesCache[movieId]
    if (cached != null && System.currentTimeMillis() - cached.at < TOTAL_EPISODES_TTL_MS) return cached.total
    val eps = runCatching { Api.episodes(movieId, force = cached != null) }.getOrNull()
    val max = eps.orEmpty().mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: 0
    if (max > 0) {
        val ids = eps.orEmpty().mapNotNull { it.id }
        TotalEpisodesCache[movieId] = CachedTotal(max, System.currentTimeMillis(), ids)
        return max
    }
    return cached?.total ?: 0
}

@Composable
fun rememberContinueWatching(history: List<Movie>): List<Movie> {
    val watches by Progress.watches.collectAsStateWithLifecycle()

    val totals =
        remember(history) {
            mutableStateMapOf<String, Int>().apply {
                history.forEach { m ->
                    val id = m.id ?: return@forEach
                    TotalEpisodesCache[id]?.total?.takeIf { it > 0 }?.let { put(id, it) }
                }
            }
        }
    LaunchedEffect(history) {
        history.forEach { m ->
            val id = m.id ?: return@forEach
            if (!Progress.isDoneWatch(m.episode_id?.let { watches[it] })) return@forEach
            launch {
                val t = fetchTotalEpisodes(id)
                if (t > 0 && totals[id] != t) totals[id] = t
            }
        }
    }
    return history.filter { m ->
        if (!Progress.isDoneWatch(m.episode_id?.let { watches[it] })) return@filter true
        val ep = m.episode_index?.toIntOrNull() ?: return@filter true
        val total = m.id?.let { totals[it] } ?: return@filter false
        total > ep
    }
}

fun invalidateTotalEpisodes() {
    TotalEpisodesCache.replaceAll { _, v -> CachedTotal(v.total, 0L, v.episodeIds) }
}

private class CachedTotal(
    val total: Int,
    val at: Long,
    val episodeIds: List<String> = emptyList(),
)

private const val TOTAL_EPISODES_TTL_MS = 10 * 60 * 1000L

private val TotalEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, CachedTotal>()

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private val ContinueImageCache = java.util.concurrent.ConcurrentHashMap<String, String>()

@Composable
private fun rememberContinueImage(
    m: Movie,
    episodeIndex: String?,
): String? {
    val movieId = m.id
    val key = "$movieId:$episodeIndex"
    val fallback = m.coverOrPoster()
    val immediate = movieId == null || episodeIndex == null || ContinueImageCache.containsKey(key)
    val settledKey by produceState<String?>(if (immediate) key else null, key) {
        if (movieId == null || episodeIndex == null || ContinueImageCache.containsKey(key)) {
            value = key
            return@produceState
        }
        val result = runSuspendCatching { Api.findEpisode(movieId, episodeIndex) }
        if (result.isSuccess) {
            ContinueImageCache[key] = result
                .getOrNull()
                ?.image
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
        }
        value = key
    }
    val preview = ContinueImageCache[key]?.takeIf { it.isNotBlank() }
    return when {
        preview != null -> preview
        settledKey == key -> fallback
        else -> null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingCard(
    m: Movie,
    modifier: Modifier,
    onLongClick: () -> Unit,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    val watch by remember(m.episode_id) { Progress.watchFlow(m.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(m.episode_id))
    val done = Progress.isDoneWatch(watch)
    val epNum = m.episode_index?.toIntOrNull()

    val w = watch
    val hasTime = !done && w != null && w.dur > 0
    val label =
        when {
            hasTime -> "${formatClock(w.pos)} / ${formatClock(w.dur)}"
            done && epNum != null -> "Ep ${epNum + 1}"
            else -> "Ep ${m.episode_index ?: "1"}"
        }
    val targetIndex = if (done && epNum != null) (epNum + 1).toString() else m.episode_index
    val image = rememberContinueImage(m, targetIndex)
    val click = rememberPrefetchOnClick(image, onClick)

    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = onLongClick, onClick = click)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(126.dp * scale, 72.dp * scale), Alignment.Center) {
            Poster(image, Modifier.matchParentSize(), 10.dp)
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White)
            }
            if (hasTime) {
                WavyLinearProgress(
                    progress = { Progress.fractionOf(w).coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x66FFFFFF),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun rememberContinueResume(
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
): (Movie) -> Unit {
    var resolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return remember(onOpen, onPlay) {
        fun resume(m: Movie) {
            val movieId = m.id ?: return
            val epId = m.episode_id
            val idx = m.episode_index
            if (epId == null || idx == null) {
                onOpen(movieId)
                return
            }
            if (!Progress.isDoneWatch(Progress.watchOf(epId))) {
                History.stage(m, idx, epId)
                onPlay(epId, "${m.title.orEmpty()} - Ep $idx", movieId, idx)
                return
            }
            if (resolving) return
            resolving = true
            scope.launch {
                try {
                    val next = (Api.lookupNextEpisode(movieId, idx) as? Api.NextEpisodeLookup.Exists)?.episode
                    val nextId = next?.id
                    if (next != null && nextId != null) {
                        History.stage(m, next.index, nextId)
                        onPlay(nextId, "${m.title.orEmpty()} - Ep ${next.index.orEmpty()}", movieId, next.index)
                    } else {
                        onOpen(movieId)
                    }
                } finally {
                    resolving = false
                }
            }
        }
        ::resume
    }
}

@Composable
fun ContinueWatchingRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    onRemove: (Movie) -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }
    val resume = rememberContinueResume(onOpen, onPlay)

    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id ?: it.hashCode() }, contentType = { "continue" }) { m ->
            ContinueWatchingCard(
                m,
                Modifier.width(280.dp * scale),
                onLongClick = { pendingRemove = m },
                scale = scale,
            ) { resume(m) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { pendingRemove = null },
            title = "Buang dari Lanjut Nonton?",
            text = { Text("Progres nonton \"${target.title.orEmpty()}\" bakal dihapus.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    onRemove(target)
                    pendingRemove = null
                }
            },
            dismissButton = {
                DialogCancelButton { pendingRemove = null }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HotBlock(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(list.distinctById(), key = { _, m -> m.listKey() }, contentType = { _, _ -> "hot" }) { index, m ->
            val statusLongClick = rememberStatusLongClick(m)
            Column(
                Modifier
                    .width(280.dp * scale)
                    .clip(RoundedCornerShape(24.dp))
                    .cardBackground()
                    .hapticClick(
                        onLongClick = statusLongClick,
                        onClick = { m.id?.let(onOpen) },
                    ).padding(8.dp),
            ) {
                Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                    Poster(
                        m.coverOrPoster(),
                        Modifier.fillMaxWidth().height(150.dp * scale),
                        radius = 16.dp,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                ),
                            ),
                    )
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "#${index + 1}",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    OverlayChip(
                        Icons.Outlined.Star,
                        fmtNum(m.favorites),
                        MaterialTheme.colorScheme.tertiary,
                        Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Poster(m.image_poster, Modifier.size(70.dp * scale, 99.dp * scale), 16.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            m.label().orEmpty(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.title.orEmpty(),
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        StatLine(
                            { ViewsBadge() },
                            "${fmtNum(m.views)} dilihat",
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomPreviewPager(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
) {
    val pager = rememberPagerState(pageCount = { list.size })
    LaunchedEffect(list) {
        if (list.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5000)
            if (pager.isScrollInProgress) continue
            val next = (pager.currentPage + 1) % list.size
            pager.animateScrollToPage(next)
        }
    }
    Column {
        val landscape = isLandscape()
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            pageSize = if (landscape) PageSize.Fixed(420.dp) else PageSize.Fill,
        ) { i ->
            val m = list[i]
            val statusLongClick = rememberStatusLongClick(m)
            val scope = rememberCoroutineScope()
            var resolving by remember { mutableStateOf(false) }
            val away = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .graphicsLayer {
                        val scale = 1f - 0.06f * away
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.4f * away
                    }.clip(RoundedCornerShape(32.dp))
                    .hapticClick(
                        onLongClick = statusLongClick,
                        onClick = { m.id?.let(onOpen) },
                    ),
            ) {
                Poster(
                    m.coverOrPoster(),
                    Modifier.fillMaxSize(),
                    32.dp,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.25f to Color.Transparent,
                                0.6f to Color.Black.copy(alpha = 0.5f),
                                1f to Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        val meta =
                            listOfNotNull(
                                m.type,
                                releaseDateLabel(m),
                            ).filter { it.isNotBlank() }.joinToString(" \u2022 ")
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            m.title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val genres =
                            m.genre
                                .orEmpty()
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(3)
                                .joinToString(" \u2022 ")
                        if (genres.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                genres,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = hapticClick {
                            val movieId = m.id ?: return@hapticClick
                            if (resolving) return@hapticClick
                            resolving = true
                            scope.launch {
                                try {
                                    val ep = runSuspendCatching { Api.findEpisode(movieId, "1") }.getOrNull()
                                    val epId = ep?.id
                                    if (ep != null && epId != null) {
                                        val idx = ep.index ?: "1"
                                        History.stage(m, idx, epId)
                                        onPlay(epId, "${m.title.orEmpty()} - Ep $idx", movieId, idx)
                                    } else {
                                        onOpen(movieId)
                                    }
                                } finally {
                                    resolving = false
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Putar Episode 1",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
        if (list.size in 2..12) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(list.size) { i ->
                    val selected = pager.currentPage == i
                    val w by animateDpAsState(if (selected) 24.dp else 6.dp, label = "dot-width")
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun MovieGrid(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    bottomPad: Dp,
    showTime: Boolean = false,
    showReleaseDate: Boolean = false,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentTopPadding(),
                bottom = bottomPad + LocalBottomInset.current,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list.distinctById(), key = { it.listKey() }, contentType = { "portrait" }) { m ->
            PortraitCard(m, Modifier.fillMaxWidth(), showTime, showReleaseDate = showReleaseDate) { m.id?.let(onOpen) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaginatedMovieGrid(
    loadKey: Any,
    loader: suspend (page: Int, force: Boolean) -> List<Movie>,
    onOpen: (String) -> Unit,
    bottomPad: Dp = 16.dp,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    pullRefreshEnabled: Boolean = true,
    genreAsChip: Boolean = false,
) {
    val viewModel: PagedListViewModel =
        viewModel(
            key = loadKey.toString(),
            factory = PagedListViewModel.factory(loader),
        )
    val paged by viewModel.state.collectAsStateWithLifecycle()
    val items = paged.items
    val loading = paged.loading
    val isRefreshing = paged.isRefreshing
    val loadingMore = paged.loadingMore
    val hasMore = paged.hasMore
    val error = paged.error
    val loadMoreFailed = paged.loadMoreFailed

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) viewModel.loadMore()
    }

    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    LaunchedEffect(online) {
        if (online && loadMoreFailed) {
            if (items.isEmpty() && error != null) {
                viewModel.retryInitial()
            } else if (shouldLoadMore) {
                viewModel.retryLoadMore()
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
        enabled = pullRefreshEnabled,
    ) {
        when {
            loading && items.isEmpty() -> GridPlaceholder()
            error != null && items.isEmpty() ->
                ErrorState(error.orEmpty(), { viewModel.retryInitial() })
            items.isEmpty() ->
                AnimatedEmptyState(icon = Icons.Outlined.SearchOff, title = "Yah, gak ada hasilnya")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = contentTopPadding(),
                            bottom =
                                bottomPad + LocalBottomInset.current,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id ?: it.hashCode() }, contentType = { "portrait" }) { m ->
                        PortraitCard(m, Modifier.fillMaxWidth(), genreAsChip = genreAsChip) { m.id?.let(onOpen) }
                    }
                    if (loadMoreFailed) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-retry") {
                            TextButton(
                                onClick = hapticClick { viewModel.retryLoadMore() },
                                modifier = Modifier.fillMaxWidth(),
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Gagal muat lagi. Coba lagi") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingGrid(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    bottomPad: Dp,
    onRemove: (Movie) -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }
    val resume = rememberContinueResume(onOpen, onPlay)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentTopPadding(),
                bottom = bottomPad + LocalBottomInset.current,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.id ?: it.hashCode() }, contentType = { "continue" }) { m ->
            ContinueWatchingCard(
                m,
                Modifier.fillMaxWidth(),
                onLongClick = { pendingRemove = m },
            ) { resume(m) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { pendingRemove = null },
            title = "Buang dari Lanjut Nonton?",
            text = { Text("Progres nonton \"${target.title.orEmpty()}\" bakal dihapus.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    onRemove(target)
                    pendingRemove = null
                }
            },
            dismissButton = {
                DialogCancelButton { pendingRemove = null }
            },
        )
    }
}
