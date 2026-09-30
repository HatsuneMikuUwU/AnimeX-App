package com.uwu.animex.ui

import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Tinggi bottom bar melayang. Layar tab nambahin ini ke bottom padding list-nya
 * biar item terakhir nggak ketutup pill. Default 0 (route tanpa bar).
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Bottom nav model "pill" melayang.
 * - Android 13+ (dan [backdrop] dikasih): liquid glass (blur + lens/refraksi) via Kyant0 Backdrop.
 * - Android 12 ke bawah: background solid.
 *
 * [backdrop] harus dipasang ke konten di belakang bar pakai `Modifier.layerBackdrop(backdrop)`.
 */
@Composable
fun FloatingNavBar(
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    val container = MaterialTheme.colorScheme.surfaceContainer
    val useGlass = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (useGlass && backdrop != null) {
            Row(
                Modifier
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(16.dp.toPx(), 32.dp.toPx())
                        },
                        onDrawSurface = { drawRect(container.copy(alpha = 0.4f)) },
                    )
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        } else {
            Surface(
                shape = shape,
                color = container,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) { content() }
            }
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
