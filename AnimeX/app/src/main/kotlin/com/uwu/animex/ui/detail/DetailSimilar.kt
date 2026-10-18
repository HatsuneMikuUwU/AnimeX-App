package com.uwu.animex.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.ui.common.PosterCardPlaceholder
import com.uwu.animex.ui.common.SimilarPortraitRow

/** Similar-anime row at the bottom of the Info tab (no header); cards carry a "xx% mirip" chip. Hidden when empty. */
@Composable
internal fun SimilarSection(
    movies: List<SimilarMovie>,
    loading: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (movies.isEmpty() && !loading) return
    if (movies.isEmpty()) {
        LazyRow(
            modifier = modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
        ) {
            items(5) { PosterCardPlaceholder(Modifier.width(105.dp)) }
        }
    } else {
        SimilarPortraitRow(list = movies, onOpen = onOpen, modifier = modifier.padding(top = 8.dp))
    }
}
