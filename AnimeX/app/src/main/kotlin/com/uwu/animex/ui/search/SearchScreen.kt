@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.TraceMoe
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.ExplorePlaceholder
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.fabBottomInset
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.viewmodel.ExploreViewModel
import com.uwu.animex.ui.viewmodel.ExploreViewModels
import com.uwu.animex.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ExploreFabClearance = 96.dp

internal fun searchSuggestionItemKey(
    index: Int,
    id: String?,
): String = "s:${id?.takeIf { it.isNotBlank() } ?: "missing"}:$index"

@Composable
fun ExploreScreen(
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpen: (String) -> Unit = {},
    onOpenCategory: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenYear: () -> Unit = {},
    onOpenType: () -> Unit = {},
) {
    BrowseCategories(
        onFilter = onFilter,
        onOpen = onOpen,
        onOpenCategory = onOpenCategory,
        onOpenStudio = onOpenStudio,
        onOpenYear = onOpenYear,
        onOpenType = onOpenType,
    )
}

private const val SUGGEST_DEBOUNCE_MS = 300L

private const val SUGGEST_MIN_CHARS = 2

@Composable
fun SearchHistoryList(
    typed: String,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit = onPick,
    onOpenSuggestion: (Movie) -> Unit = { onPick(it.title.orEmpty()) },
    searchViewModel: SearchViewModel = viewModel(),
) {
    val all by SearchHistory.items.collectAsStateWithLifecycle()
    val shown =
        remember(all, typed) {
            val t = typed.trim()
            if (t.isEmpty()) all else all.filter { it.contains(t, ignoreCase = true) }
        }

    val suggestionsState by searchViewModel.suggestions.collectAsStateWithLifecycle()
    LaunchedEffect(typed) {
        searchViewModel.onQueryChanged(typed, SUGGEST_DEBOUNCE_MS, SUGGEST_MIN_CHARS)
    }
    val suggestions = (suggestionsState as? UiState.Ready)?.value.orEmpty()
    val loading = suggestionsState is UiState.Loading

    if (shown.isEmpty() && suggestions.isEmpty()) {
        when {
            loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
            all.isEmpty() && typed.isBlank() ->
                AnimatedEmptyState(
                    icon = Icons.Outlined.History,
                    title = "Belum ada yang kamu cari nih",
                    message = "Riwayat pencarianmu bakal muncul di sini.",
                )
            else ->
                AnimatedEmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Gak ada yang nyambung",
                    message = "Coba pakai kata kunci lain.",
                )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (suggestions.isNotEmpty()) {
            item(key = "suggest-header") {
                Text(
                    "Saran",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(
                suggestions,
                key = { index, movie -> searchSuggestionItemKey(index, movie.id) },
                contentType = { _, _ -> "suggestion" },
            ) { _, movie ->
                val title = movie.title.orEmpty()
                val meta =
                    listOfNotNull(movie.type, movie.year)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                ListItem(
                    leadingContent = {
                        AsyncImage(
                            model = Api.absUrl(movie.image_poster),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(width = 40.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = hapticClick { onFill(title) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.NorthWest, contentDescription = "Pakai judul ini")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = hapticClick { onOpenSuggestion(movie) }),
                ) {
                    Column {
                        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (shown.isNotEmpty()) {
            if (typed.isBlank()) {
                item(key = "history-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Riwayat",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = hapticClick { SearchHistory.clear() },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Bersihin semua") }
                    }
                }
            } else if (suggestions.isNotEmpty()) {
                item(key = "history-header-typed") {
                    Text(
                        "Riwayat",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
            }
            items(shown, key = { "h:$it" }, contentType = { "history" }) { item ->
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                    trailingContent = {
                        IconButton(
                            onClick = hapticClick { SearchHistory.remove(item) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Hapus")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = hapticClick { onPick(item) }),
                ) {
                    Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BrowseCategories(
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenYear: () -> Unit,
    onOpenType: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val fabExpanded = isListScrollingUp(listState)

    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TraceMoe.Result>?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            cropUri = uri
        }

    fun searchCroppedImage(bitmap: Bitmap) {
        scope.launch {
            searching = true
            results = null
            try {
                val jpeg =
                    withContext(Dispatchers.IO) {
                        java.io.ByteArrayOutputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                            out.toByteArray()
                        }
                    }
                val hits = TraceMoe.search(jpeg)
                if (hits.isEmpty()) {
                    snackbar.show(scope, "Gak ketemu anime dari gambar itu")
                } else {
                    results = hits
                }
            } catch (t: Throwable) {
                snackbar.show(scope, t.message?.take(120) ?: "Gagal cari dari gambar")
            } finally {
                searching = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val exploreVm: ExploreViewModel = viewModel(factory = ExploreViewModels.exploreFactory())
        val exploreState by exploreVm.state.collectAsStateWithLifecycle()
        val exploreRefreshing by exploreVm.isRefreshing.collectAsStateWithLifecycle()
        ExpressivePullToRefreshBox(
            isRefreshing = exploreRefreshing,
            onRefresh = { exploreVm.refresh() },
            errors = exploreVm.refreshError,
            modifier = Modifier.fillMaxSize(),
        ) {
            UiStateContent(
                state = exploreState,
                onRetry = { exploreVm.refresh() },
                loading = { ExplorePlaceholder() },
                error = {
                    CategoryContent(
                        ExploreData(),
                        listState,
                        onFilter,
                        onOpenCategory,
                        onOpenStudio,
                        onOpenYear,
                        onOpenType,
                    )
                },
            ) { data ->
                CategoryContent(
                    data,
                    listState,
                    onFilter,
                    onOpenCategory,
                    onOpenStudio,
                    onOpenYear,
                    onOpenType,
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = hapticClick {
                if (!searching) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            expanded = fabExpanded && !searching,
            shape = RoundedCornerShape(16.dp),
            icon = {
                if (searching) {
                    LoadingIndicator(
                        Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Outlined.ImageSearch, contentDescription = "Cari dari gambar")
                }
            },
            text = { Text(if (searching) "Nyari…" else "Cari dari gambar") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = fabBottomInset()),
        )

        val shown = results
        BackHandler(enabled = cropUri != null) { cropUri = null }
        BackHandler(enabled = shown != null) { results = null }
        AnimatedVisibility(
            visible = shown != null,
            enter = AppMotion.fadeEnter(),
            exit = AppMotion.fadeExit(),
        ) {
            if (shown != null) {
                ImageSearchResults(
                    results = shown,
                    resolvingTitle = resolvingId,
                    onPick = { hit ->
                        scope.launch {
                            resolvingId = hit.displayTitle
                            try {
                                val movies = Api.search(hit.displayTitle)
                                val id = movies.firstOrNull()?.id
                                if (id != null) {
                                    results = null
                                    onOpen(id)
                                } else {
                                    snackbar.show(scope, "\"${hit.displayTitle}\" belum ada di katalog")
                                }
                            } catch (t: Throwable) {
                                snackbar.show(scope, t.message?.take(100) ?: "Gagal buka di katalog")
                            } finally {
                                resolvingId = null
                            }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier.align(Alignment.BottomCenter).padding(bottom = ExploreFabClearance + LocalBottomInset.current),
        )

        cropUri?.let { uri ->
            ImageCropScreen(
                uri = uri,
                onBack = { cropUri = null },
                onCrop = { bitmap ->
                    cropUri = null
                    searchCroppedImage(bitmap)
                },
            )
        }
    }
}
