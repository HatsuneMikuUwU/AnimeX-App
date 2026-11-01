@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.AccentPalette
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.seedColor
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.paletteColorScheme
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Composable
internal fun ColorSchemePreview() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val swatch =
        if (settings.customSeed != 0) {
            rememberSeedScheme(settings.seedColor)
        } else {
            rememberSwatchScheme(settings.accent)
        }
    val scheme = swatch ?: MaterialTheme.colorScheme
    val primaryArc = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryArc = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryArc = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val backdrop = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(72.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(color = primaryArc, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryArc, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryArc, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Ubah",
                    modifier = Modifier.size(16.dp),
                    tint = scheme.inversePrimary,
                )
            }
        }
    }
}

private const val PaletteColumns = 3

internal val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private val SwatchSchemeCache = ConcurrentHashMap<String, ColorScheme>()

@Composable
internal fun AccentSheet(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            AccentPalette.entries.chunked(PaletteColumns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { accent ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AccentSwatch(accent, accent == selected) { onSelect(accent) }
                        }
                    }
                    repeat(PaletteColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun rememberSwatchScheme(accent: AccentPalette): ColorScheme? {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val cacheKey = "${accent.name}_${settings.paletteStyle.name}_${settings.colorSpec.name}"
    val scheme by produceState<ColorScheme?>(initialValue = SwatchSchemeCache[cacheKey], key1 = cacheKey) {
        val cached = SwatchSchemeCache[cacheKey]
        if (cached != null) {
            value = cached
        } else {
            withContext(Dispatchers.Default) {
                val computed = paletteColorScheme(accent.seed, false, settings.paletteStyle, settings.colorSpec)
                SwatchSchemeCache[cacheKey] = computed
                value = computed
            }
        }
    }
    return scheme
}

@Composable
private fun AccentSwatch(
    accent: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberSwatchScheme(accent)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = hapticClick(block = onClick), role = Role.Button)
                .padding(vertical = 8.dp),
    ) {
        val current = scheme
        if (current != null) {
            SwatchContent(current, selected)
        } else {
            FallbackSwatchContent(accent.seed, selected)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            accent.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SwatchContent(
    scheme: ColorScheme,
    selected: Boolean,
) {
    val primaryArc = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryArc = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryArc = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val backdrop = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }
    Box(
        Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(color = primaryArc, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryArc, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryArc, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = scheme.inversePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FallbackSwatchContent(
    base: Color,
    selected: Boolean,
) {
    Box(
        Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(base.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(base.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(base),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
