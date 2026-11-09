package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun UserAvatar(
    username: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val key = username?.trim()?.lowercase().orEmpty()
    val container =
        remember(key, dark) {
            if (key.isEmpty()) null else avatarColors(key, dark)
        }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container?.first ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            username?.trim()?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = container?.second ?: MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun avatarColors(
    key: String,
    dark: Boolean,
): Pair<Color, Color> {
    val hue = (key.hashCode().toLong().mod(360L)).toFloat()
    return if (dark) {
        Color.hsl(hue, 0.35f, 0.28f) to Color.hsl(hue, 0.6f, 0.85f)
    } else {
        Color.hsl(hue, 0.55f, 0.86f) to Color.hsl(hue, 0.6f, 0.25f)
    }
}
