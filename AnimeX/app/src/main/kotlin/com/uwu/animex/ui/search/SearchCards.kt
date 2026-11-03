@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.hapticClick

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TypeCard(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    showTrailing: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val palette = remember(label, cs) { TypeCardPalette.pick(label, cs) }
    val badgeShape = remember(label) { TypeCardPalette.badgePolygon(label) }.toShape()

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "type-card-corner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "type-card-scale",
    )

    Surface(
        onClick = hapticClick(block = onClick),
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = palette.container,
        contentColor = palette.onContainer,
        modifier =
            modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            Modifier
                .heightIn(min = if (supporting != null) 80.dp else 68.dp)
                .padding(start = 12.dp, end = if (showTrailing) 16.dp else 22.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(badgeShape)
                    .background(palette.badge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        label
                            .trim()
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.onBadge,
                )
            }
            Column(
                modifier = if (showTrailing) Modifier.weight(1f) else Modifier,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showTrailing) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.onContainer.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private class TypeCardColors(
    val container: Color,
    val onContainer: Color,
    val badge: Color,
    val onBadge: Color,
)

private object TypeCardPalette {
    fun pick(
        label: String,
        cs: androidx.compose.material3.ColorScheme,
    ): TypeCardColors =
        when (index(label) % 3) {
            0 -> TypeCardColors(cs.primaryContainer, cs.onPrimaryContainer, cs.primary, cs.onPrimary)
            1 -> TypeCardColors(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary, cs.onSecondary)
            else -> TypeCardColors(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary, cs.onTertiary)
        }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun badgePolygon(label: String) =
        run {
            val shapes =
                listOf(
                    MaterialShapes.Cookie6Sided,
                    MaterialShapes.Clover4Leaf,
                    MaterialShapes.Flower,
                    MaterialShapes.SoftBurst,
                    MaterialShapes.Cookie9Sided,
                    MaterialShapes.Pentagon,
                )
            shapes[(index(label) / 3) % shapes.size]
        }

    private fun index(label: String): Int = label.lowercase().hashCode().let { it xor (it ushr 16) } and 0x7fffffff
}

@Composable
fun GenreCard(
    item: ExploreItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainer
    val bg = remember(item.color, surfaceFallback) { parseColor(item.color) ?: surfaceFallback }
    Box(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(onClick = hapticClick(block = onClick), role = Role.Button),
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                            ),
                        startX = 0f,
                        endX = 420f,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
        ) {
            Text(
                item.subtitle ?: "Genre",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun YearCard(
    item: ExploreItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainer
    val bg = remember(item.color, surfaceFallback) { parseColor(item.color) ?: surfaceFallback }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(onClick = hapticClick(block = onClick), role = Role.Button),
    ) {
        val widthPx = remember(maxWidth, density) { with(density) { maxWidth.toPx() } }
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.55f),
                            ),
                        startX = widthPx - 420f,
                        endX = widthPx,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                item.subtitle ?: "Tahun",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching {
        val h = hex.removePrefix("#")
        when (h.length) {
            6, 8 -> Color(android.graphics.Color.parseColor("#$h"))
            else -> null
        }
    }.getOrNull()
}
