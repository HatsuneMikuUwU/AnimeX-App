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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3

/**
 * Tinggi bottom bar melayang. Layar tab nambahin ini ke bottom padding list-nya
 * biar item terakhir nggak ketutup pill. Default 0 (route tanpa bar).
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Bottom nav model "pill" melayang, gaya Telegram + liquid glass.
 * Pakai Haze Glass + Material 3 supaya tint & surface menyatu dengan tema M3.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun FloatingNavBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    // surfaceContainer agar pill naik satu tingkat dari background, tapi tetap M3
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    val glassModifier = if (hazeState != null) {
        // GlassStyle.Material3() ambil surface dari theme; kita override container
        // ke surfaceContainer biar lebih “elevated” seperti pill, lalu shape & tint
        // diset biar edge refraction + specular nyatu dengan Material.
        val style = remember(containerColor) {
            GlassStyle.Material3(containerColor = containerColor) {
                shape(shape)
                // Tint ringan dari surface sendiri → tetap mengikuti light/dark & dynamic color
                tint(containerColor.copy(alpha = 0.55f))
                // Sedikit specular biar kesan liquid glass, tidak terlalu “iOS pure”
                specularIntensity(0.35f)
                ambientResponse(0.12f)
            }
        }
        Modifier
            .clip(shape)
            .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = style,
            )
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
            modifier = glassModifier,
            shape = shape,
            color = if (hazeState != null) {
                Color.Transparent
            } else {
                // Fallback tanpa haze: semi-opaque surfaceContainer
                containerColor.copy(alpha = 0.94f)
            },
            shadowElevation = if (hazeState != null) 0.dp else 10.dp,
            // Border tipis pakai outlineVariant biar tetap “Material”, bukan pure glass
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            ),
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
