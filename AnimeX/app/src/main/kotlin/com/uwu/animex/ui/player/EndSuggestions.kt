@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.malPoster

@Composable
internal fun EndSuggestions(
    animeTitle: String,
    movies: List<SimilarMovie>,
    loading: Boolean,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (movies.isEmpty() && !loading) return
    Box(
        modifier
            .background(Color(0xE6000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ).safeDrawingPadding(),
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp).padding(end = 48.dp)) {
                Text(
                    "Lanjut nonton yang lain?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (movies.isEmpty()) "Nyari rekomendasi…" else "Rekomendasi mirip $animeTitle",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(movies, key = { it.movie.id ?: it.movie.title.orEmpty() }) { sm ->
                    val m = sm.movie
                    Column(
                        Modifier
                            .width(96.dp)
                            .let { base ->
                                val id = m.id
                                if (id != null) base.hapticClick(onClick = { onOpen(id) }) else base
                            },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box {
                            Poster(malPoster(m), Modifier.fillMaxWidth().aspectRatio(2f / 3f), radius = 12.dp)
                            Text(
                                if (sm.percent >= 100) "Saran" else "${sm.percent}%",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            m.title.orEmpty(),
                            modifier = Modifier.padding(horizontal = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        FilledTonalIconButton(
            onClick = hapticClick(block = onDismiss),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Tutup")
        }
    }
}
