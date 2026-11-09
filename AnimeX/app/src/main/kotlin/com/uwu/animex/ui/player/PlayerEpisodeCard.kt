package com.uwu.animex.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipEpisode
import com.uwu.animex.data.model.Episode
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.hapticClick

@Composable
internal fun PlayerEpisodeCard(
    ep: Episode,
    meta: AniZipEpisode?,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val name = meta?.title ?: ep.title
    val title = if (name.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. $name"
    val extraInfo =
        listOfNotNull(
            meta?.rating?.let { "Nilai: ${it.replace('.', ',')}" },
            meta?.runtime?.let { "${it}m" },
            AniZip.formatDate(meta?.airDate),
        )
    val overview = meta?.overview?.trim()?.takeIf { it.isNotEmpty() }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (current) cs.primaryContainer else cs.surfaceContainer,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .hapticClick(onClick = onClick),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                Poster(
                    meta?.image ?: ep.image?.takeIf { it.isNotBlank() },
                    Modifier.size(126.dp, 72.dp),
                    10.dp,
                    keepPrevious = true,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).heightIn(min = 72.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val info = extraInfo.joinToString("  •  ").ifEmpty { ep.key_time.orEmpty() }
                    if (info.isNotBlank()) {
                        Text(
                            info,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (overview != null) {
                Text(
                    overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}
