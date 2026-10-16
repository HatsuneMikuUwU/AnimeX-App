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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.ExploreListPlaceholder
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.viewmodel.ExploreViewModels
import com.uwu.animex.ui.viewmodel.GenresViewModel
import com.uwu.animex.ui.viewmodel.LoadViewModel
import com.uwu.animex.ui.viewmodel.StudiosViewModel
import com.uwu.animex.ui.viewmodel.TypesViewModel
import com.uwu.animex.ui.viewmodel.YearsViewModel

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    val vm: GenresViewModel = viewModel(factory = ExploreViewModels.genresFactory())
    ExploreListScaffold(
        title = "Kategori",
        onBack = onBack,
        emptyMessage = "Kategorinya kosong nih",
        viewModel = vm,
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
    val vm: StudiosViewModel = viewModel(factory = ExploreViewModels.studiosFactory())
    ExploreListScaffold(
        title = "Studio",
        onBack = onBack,
        emptyMessage = "Studionya kosong nih",
        viewModel = vm,
        cardHeight = 80.dp,
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
    val vm: TypesViewModel = viewModel(factory = ExploreViewModels.typesFactory())
    ExploreListScaffold(
        title = "Tipe",
        onBack = onBack,
        emptyMessage = "Tipenya kosong nih",
        viewModel = vm,
        cardHeight = 80.dp,
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
    val vm: YearsViewModel = viewModel(factory = ExploreViewModels.yearsFactory())
    ExploreListScaffold(
        title = "Tahun",
        onBack = onBack,
        emptyMessage = "Tahunnya kosong nih",
        viewModel = vm,
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
    viewModel: LoadViewModel<List<ExploreItem>>,
    cardHeight: Dp = 92.dp,
    itemContent: @Composable (ExploreItem) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    @Suppress("UNUSED_VARIABLE")
    val _refreshing = isRefreshing
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
                        onClick = hapticClick(block = onBack),
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
            UiStateContent(
                state = state,
                onRetry = { viewModel.refresh() },
                loading = { ExploreListPlaceholder(cardHeight) },
            ) { list ->
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
                            Box(Modifier.padding(vertical = 4.dp)) {
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
