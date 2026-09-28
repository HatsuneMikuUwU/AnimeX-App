@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.uwu.animex.data.Api
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.Episode
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import com.uwu.animex.data.Server
import com.uwu.animex.data.WatchStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onPlay: (episodeId: String, title: String) -> Unit) {
    val state = rememberLoad("detail" to id) { _ ->
        coroutineScope {
            val m = async { Api.detail(id) }
            val e = async { Api.episodes(id) }
            m.await() to e.await()
        }
    }.state
    val movie = (state as? UiState.Ready)?.value?.first
    val movieId = movie?.id ?: id
    var showStatusSheet by remember(id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fabExpanded = isScrollingUp(listState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (movie != null) {
                        val fav = Bookmarks.isFavorite(movieId)
                        IconButton(onClick = { Bookmarks.setFavorite(movie.copy(id = movieId), !fav) }) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (fav) "Hapus dari favorit" else "Tambah ke favorit",
                                tint = if (fav) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (movie != null) {
                val status = Bookmarks.status(movieId)
                ExtendedFloatingActionButton(
                    onClick = { showStatusSheet = true },
                    expanded = fabExpanded,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    text = { Text(status?.label ?: "Atur Status") },
                )
            }
        },
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
            is UiState.Ready -> {
                val (m, firstEps) = s.value
                EpisodeListContent(
                    id = id,
                    movie = m,
                    initialEpisodes = firstEps,
                    modifier = Modifier.padding(pad),
                    listState = listState,
                    onPlay = onPlay,
                )
            }
        }
    }

    if (showStatusSheet && movie != null) {
        WatchStatusSheet(
            current = Bookmarks.status(movieId),
            onDismiss = { showStatusSheet = false },
            onSelect = { newStatus ->
                Bookmarks.setStatus(movie.copy(id = movieId), newStatus)
                showStatusSheet = false
            },
        )
    }
}

@Composable
private fun isScrollingUp(listState: LazyListState): Boolean {
    var previousIndex by remember(listState) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember(listState) { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    return remember(listState) {
        derivedStateOf {
            val up = if (previousIndex != listState.firstVisibleItemIndex) {
                previousIndex > listState.firstVisibleItemIndex
            } else {
                previousOffset >= listState.firstVisibleItemScrollOffset
            }
            previousIndex = listState.firstVisibleItemIndex
            previousOffset = listState.firstVisibleItemScrollOffset
            up
        }
    }.value
}

@Composable
private fun WatchStatusSheet(
    current: WatchStatus?,
    onDismiss: () -> Unit,
    onSelect: (WatchStatus?) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            WatchStatus.entries.forEach { option ->
                WatchStatusRow(option.label, selected = current == option) { onSelect(option) }
            }
            WatchStatusRow("Tidak Ada", selected = current == null) { onSelect(null) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WatchStatusRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EpisodeListContent(
    id: String,
    movie: Movie?,
    initialEpisodes: List<Episode>,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    onPlay: (episodeId: String, title: String) -> Unit,
) {
    val title = movie?.title.orEmpty()
    var episodes by remember(id) { mutableStateOf(initialEpisodes) }
    var nextPage by remember(id) { mutableIntStateOf(1) }
    var loadingMore by remember(id) { mutableStateOf(false) }
    var hasMore by remember(id) {
        mutableStateOf(initialEpisodes.size >= 25)
    }
    val histIdx = remember(id, movie?.id) {
        History.items.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
    }
    var playTarget by remember(id) { mutableStateOf<Episode?>(null) }
    var isResumeTarget by remember(id) { mutableStateOf(false) }
    var isContinueNext by remember(id) { mutableStateOf(false) }
    var playResolving by remember(id) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var pendingEp by remember { mutableStateOf<Episode?>(null) }
    var pick by remember { mutableStateOf<Pair<Episode, List<Server>>?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun startDownload(ep: Episode, server: Server) {
        val epId = ep.id ?: return
        val link = server.link ?: return
        Downloads.enqueue(
            ctx, epId, link,
            Downloads.Meta(
                movieId = movie?.id ?: id,
                movieTitle = title,
                epIndex = ep.index,
                epTitle = ep.title,
                image = movie?.image_poster,
                quality = server.quality,
            ),
        )
        Toast.makeText(ctx, "Mengunduh Episode ${ep.index.orEmpty()}", Toast.LENGTH_SHORT).show()
    }

    fun loadServers(ep: Episode) {
        val epId = ep.id ?: return
        scope.launch {
            runCatching { Api.servers(epId) }
                .onSuccess { all ->
                    val direct = all
                        .filter { it.isDirect && !it.link.isNullOrBlank() }
                        .sortedByDescending { it.qualityValue }
                    when {
                        direct.isEmpty() ->
                            Toast.makeText(ctx, "Tidak ada server yang bisa diunduh", Toast.LENGTH_SHORT).show()
                        direct.size == 1 -> startDownload(ep, direct.first())
                        else -> pick = ep to direct
                    }
                }
                .onFailure {
                    Toast.makeText(ctx, "Gagal memuat server: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun proceedDownload(ep: Episode) {
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        loadServers(ep)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val ep = pendingEp
        pendingEp = null
        if (uri != null && ep != null) {
            Downloads.setFolder(ctx, uri)
            proceedDownload(ep)
        }
    }

    pick?.let { (ep, servers) ->
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("Pilih kualitas") },
            text = {
                Column {
                    servers.forEach { sv ->
                        Text(
                            sv.quality?.takeIf { it.isNotBlank() } ?: "Default",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pick = null; startDownload(ep, sv) }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = null }) { Text("Batal") } },
        )
    }

    val download: (Episode) -> Unit = { ep ->
        if (ep.id != null) {
            val folderOk = Downloads.folderUri?.let {
                runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(it))?.canWrite() == true }.getOrDefault(false)
            } == true
            if (folderOk) {
                proceedDownload(ep)
            } else {
                pendingEp = ep
                folderPicker.launch(Downloads.folderUri?.let(Uri::parse))
            }
        }
    }

    LaunchedEffect(id) {
        playResolving = true
        val newest = initialEpisodes.maxByOrNull { it.index?.toIntOrNull() ?: Int.MIN_VALUE }
        val shortFirst = initialEpisodes
            .minByOrNull { it.index?.toIntOrNull() ?: Int.MAX_VALUE }
            ?.takeIf { (it.index?.toIntOrNull() ?: Int.MAX_VALUE) <= 1 }

        var resume: Episode? = null
        if (histIdx != null) {
            resume = initialEpisodes.firstOrNull { it.index == histIdx }
                ?: runCatching { Api.findEpisode(id, histIdx) }.getOrNull()
        }

        var continueNext: Episode? = null
        if (resume != null && Progress.isDone(resume.id)) {
            val nextIdx = histIdx?.toIntOrNull()?.plus(1)?.toString()
            continueNext = nextIdx?.let { idx ->
                initialEpisodes.firstOrNull { it.index == idx }
                    ?: runCatching { Api.findEpisode(id, idx) }.getOrNull()
            }
        }

        val first = shortFirst
            ?: if (resume == null) runCatching { Api.firstEpisode(id) }.getOrNull() else null

        playTarget = continueNext ?: resume ?: first ?: newest
        isContinueNext = continueNext != null
        isResumeTarget = resume != null && continueNext == null
        playResolving = false
    }

    val play: (Episode) -> Unit = { ep ->
        ep.id?.let { epId ->
            movie?.let { History.record(it.copy(id = it.id ?: id), ep.index, epId) }
            onPlay(epId, "$title - Ep ${ep.index.orEmpty()}")
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = nextPage
                val more = Api.episodes(id, page = page)
                if (more.isEmpty()) {
                    hasMore = false
                } else {
                    val seen = episodes.mapNotNull { it.id }.toHashSet()
                    val fresh = more.filter { it.id != null && it.id !in seen }
                    if (fresh.isEmpty()) {
                        hasMore = false
                    } else {
                        episodes = episodes + fresh
                        nextPage = page + 1
                        if (more.size < 20) hasMore = false
                    }
                }
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) loadMore()
    }

    LazyColumn(modifier = modifier, state = listState) {
        item {
            Header(
                id = id,
                movie,
                episodes,
                playTarget = playTarget,
                isResume = isResumeTarget,
                isContinueNext = isContinueNext,
                resolving = playResolving,
                histIdx = histIdx,
                onPlay = play,
            )
        }
        item {
            val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
                ?: episodes.size
            Text(
                "$totalEps Episode",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            )
        }
        items(episodes, key = { it.id ?: "${it.index}-${it.title}" }) { ep ->
            EpisodeRow(
                ep,
                download = Downloads.item(ep.id),
                onDownload = { download(ep) },
            ) { play(ep) }
        }
        if (loadingMore) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun Header(
    id: String,
    m: Movie?,
    eps: List<Episode>,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
) {
    if (m == null) return
    Column {
        Poster(
            m.image_cover ?: m.image_poster,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f),
            28.dp,
            sharedKey = "cover-$id",
        )
        Row(Modifier.padding(16.dp)) {
            Poster(m.image_poster, Modifier.size(100.dp, 150.dp), 18.dp, sharedKey = "poster-$id")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (!m.studio.isNullOrBlank()) {
                    Text(
                        m.studio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${fmtNum(m.views)} views • ${fmtNum(m.favorites)} favorit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val genres = m.genre.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { g ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(g, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(50),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = null,
                    )
                }
            }
        }
        Button(
            onClick = { playTarget?.let(onPlay) },
            enabled = playTarget != null && !resolving,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            if (resolving && playTarget == null) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (histIdx != null) "Lanjutkan Episode $histIdx"
                    else "Memuat…",
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isContinueNext && playTarget != null ->
                            "Lanjutkan ke Episode ${playTarget.index.orEmpty()}"
                        isResume && playTarget != null ->
                            "Lanjutkan Episode ${playTarget.index.orEmpty()}"
                        playTarget != null ->
                            "Putar Episode ${playTarget.index.orEmpty()}"
                        else -> "Belum ada episode"
                    },
                )
            }
        }
        if (!m.synopsis.isNullOrBlank()) {
            Text(
                m.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    download: Downloads.Item?,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val progress = Progress.fraction(ep.id)
    val done = Progress.isDone(ep.id)
    val title = if (ep.title.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. ${ep.title}"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                Poster(ep.image, Modifier.matchParentSize(), 10.dp)
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = "Sudah ditonton", tint = Color.White)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
                if (!done && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0x66FFFFFF),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.key_time?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            DownloadButton(download, onStart = onDownload)
        }
    }
}

@Composable
private fun DownloadButton(item: Downloads.Item?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (item == null) onStart() else menu = true }) {
            if (item == null) {
                Icon(Icons.Filled.Download, contentDescription = "Unduh")
            } else {
                when (item.status) {
                    Downloads.Status.QUEUED ->
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Downloads.Status.DOWNLOADING ->
                        if (item.percent >= 0f) {
                            CircularProgressIndicator(
                                progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    Downloads.Status.PAUSED -> Icon(Icons.Filled.Pause, contentDescription = "Dijeda")
                    Downloads.Status.COMPLETED -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Terunduh",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Downloads.Status.FAILED -> Icon(
                        Icons.Filled.Error,
                        contentDescription = "Gagal",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item == null) return@DropdownMenu
            val id = item.id
            when (item.status) {
                Downloads.Status.QUEUED, Downloads.Status.DOWNLOADING -> {
                    DropdownMenuItem(
                        text = { Text("Jeda") },
                        onClick = { menu = false; Downloads.pause(ctx, id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Batalkan") },
                        onClick = { menu = false; Downloads.remove(ctx, id) },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjutkan") },
                        onClick = { menu = false; Downloads.resume(ctx, id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = { menu = false; Downloads.remove(ctx, id) },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi") },
                        onClick = { menu = false; Downloads.retry(ctx, id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = { menu = false; Downloads.remove(ctx, id) },
                    )
                }
                Downloads.Status.COMPLETED -> DropdownMenuItem(
                    text = { Text("Hapus file unduhan") },
                    onClick = { menu = false; Downloads.remove(ctx, id) },
                )
            }
        }
    }
}
