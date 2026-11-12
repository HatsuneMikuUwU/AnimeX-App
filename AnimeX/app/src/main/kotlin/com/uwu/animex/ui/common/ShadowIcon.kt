package com.uwu.animex.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShadowIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.White,
    shadowColor: Color = Color.Black.copy(alpha = 0.55f),
) {
    Box(modifier.size(size)) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = shadowColor,
            modifier = Modifier.size(size).offset(y = 1.dp).blur(2.dp),
        )
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size))
    }
}
