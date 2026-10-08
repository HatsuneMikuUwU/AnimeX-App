@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.animateItem
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.rememberBlurBackdrop

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Kategori",
        onBack = onBack,
        emptyMessage = "Kategorinya kosong nih",
        loadKey = "explore-genres",
        loader = { force -> Api.exploreGenres(force) },
    ) { item ->
        GenreCard(item, modifier = Modifier.fillMaxWidth()) {
            val filterId = item.id?.takeIf { it.isNotBlank() } ?: item.displayName
            onFilter("genre", filterId, item.displayName)
        }
    }
}

@Composable
fun StudioScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Studio",
        onBack = onBack,
        emptyMessage = "Studionya kosong nih",
        loadKey = "explore-studios",
        loader = { force -> Api.exploreStudios(force) },
    ) { item ->
        TypeCard(
            item.displayName,
            modifier = Modifier.fillMaxWidth(),
            supporting = "Studio anime",
            showTrailing = true,
        ) {
            onFilter("studio", item.displayName, item.displayName)
        }
    }
}

@Composable
fun TypeScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tipe",
        onBack = onBack,
        emptyMessage = "Tipenya kosong nih",
        loadKey = "explore-types",
        loader = { force -> Api.explore(force, preview = false).typeOrDefault },
    ) { item ->
        TypeCard(
            item.displayName,
            modifier = Modifier.fillMaxWidth(),
            supporting = "Format tayang",
            showTrailing = true,
        ) {
            onFilter("type", item.displayName, item.displayName)
        }
    }
}

@Composable
fun YearScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tahun",
        onBack = onBack,
        emptyMessage = "Tahunnya kosong nih",
        loadKey = "explore-years",
        loader = { force -> Api.exploreYears(force) },
    ) { item ->
        YearCard(item, modifier = Modifier.fillMaxWidth()) {
            onFilter("year", item.displayName, item.displayName)
        }
    }
}

@Composable
private fun ExploreListScaffold(
    title: String,
    onBack: () -> Unit,
    emptyMessage: String,
    loadKey: String,
    loader: suspend (Boolean) -> List<ExploreItem>,
    itemContent: @Composable (ExploreItem) -> Unit,
) {
    val load = rememberLoad(loadKey) { force -> loader(force) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(backdrop, blendColor = MaterialTheme.colorScheme.background),
                expandedHeight = 160.dp,
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                        scrolledContainerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        BlurContentBox(pad, backdrop) {
            UiStateContent(state = load.state, onRetry = load.refresh) { list ->
                if (list.isEmpty()) {
                    CenterText(emptyMessage)
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp + LocalTopInset.current,
                                bottom = 8.dp + LocalBottomInset.current,
                            ),
                    ) {
                        val unique = list.distinctBy { it.id ?: it.displayName }
                        items(
                            unique,
                            key = { it.id ?: it.displayName },
                            contentType = { "category-item" },
                        ) { item ->
                            Box(Modifier.padding(vertical = 4.dp).animateItem()) {
                                itemContent(item)
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}
