@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.WatchStatus

private enum class BookmarkFilter(val label: String, val status: WatchStatus?) {
    WATCHING(WatchStatus.WATCHING.label, WatchStatus.WATCHING),
    COMPLETED(WatchStatus.COMPLETED.label, WatchStatus.COMPLETED),
    ON_HOLD(WatchStatus.ON_HOLD.label, WatchStatus.ON_HOLD),
    DROPPED(WatchStatus.DROPPED.label, WatchStatus.DROPPED),
    PLAN_TO_WATCH(WatchStatus.PLAN_TO_WATCH.label, WatchStatus.PLAN_TO_WATCH),
    FAVORITE("Favorit", null),
}

@Composable
fun BookmarkScreen(onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(BookmarkFilter.WATCHING) }

    Column(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = filter.ordinal)

        LaunchedEffect(filter) {
            if (listState.firstVisibleItemIndex != filter.ordinal) {
                listState.animateScrollToItem(filter.ordinal)
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BookmarkFilter.entries) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
                    leadingIcon = if (filter == f) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null,
                )
            }
        }
        val list = if (filter == BookmarkFilter.FAVORITE) Bookmarks.favorites else Bookmarks.byStatus(filter.status!!)
        if (list.isEmpty()) {
            CenterText("Belum ada anime di \"${filter.label}\"")
        } else {
            MovieGrid(list, onOpen, bottomPad = 16.dp)
        }
    }
}
