package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.Api

@Composable
fun UserAvatar(
    username: String?,
    imageUrl: String? = null,
    pokemon: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val image = sequenceOf(imageUrl, pokemon)
        .filterNotNull()
        .map { it.trim() }
        .firstOrNull { value ->
            value.isNotBlank() &&
                (value.startsWith("http://", true) || value.startsWith("https://", true) || '/' in value || '.' in value)
        }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            username?.trim()?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (image != null) {
            AsyncImage(
                model = Api.absUrl(image),
                contentDescription = "Foto profil ${username ?: "pengguna"}",
                modifier = Modifier.matchParentSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
