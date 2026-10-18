package com.uwu.animex.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.PortraitRow
import com.uwu.animex.ui.common.PosterCardPlaceholder
import com.uwu.animex.ui.common.SectionHeader

/** "Rekomendasi" row at the bottom of the Info tab. Hidden when there is nothing to recommend. */
@Composable
internal fun SimilarSection(
    movies: List<Movie>,
    loading: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (movies.isEmpty() && !loading) return
    Column(modifier) {
        SectionHeader(title = "Rekomendasi", onMore = null, icon = Icons.Outlined.AutoAwesome)
        if (movies.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
            ) {
                items(5) { PosterCardPlaceholder(Modifier.width(105.dp)) }
            }
        } else {
            PortraitRow(list = movies, onOpen = onOpen)
        }
    }
}
