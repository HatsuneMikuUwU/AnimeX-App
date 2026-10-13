@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.uwu.animex.ui.thread

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.anilist.AniListThreads
import com.uwu.animex.sync.providers.AniThread
import com.uwu.animex.sync.providers.AniThreadComment
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.MarkdownText
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
}

private fun relativeTime(createdAt: Int): String {
    if (createdAt <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = (now - createdAt).coerceAtLeast(0)
    return when {
        diff < 60 -> "baru saja"
        diff < 3600 -> "${diff / 60} menit lalu"
        diff < 86400 -> "${diff / 3600} jam lalu"
        diff < 604800 -> "${diff / 86400} hari lalu"
        else -> {
            val fmt = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))
            fmt.format(Date(createdAt * 1000L))
        }
    }
}

/** Open text in Google Translate (or any translate activity). Mirrors AniHyou's TranslateIconButton. */
private fun openTranslate(context: android.content.Context, text: String) {
    val plain = stripHtml(text)
    if (plain.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, plain)
        // Prefer Google Translate if installed
        setPackage("com.google.android.apps.translate")
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, plain)
        }
        context.startActivity(Intent.createChooser(fallback, "Terjemahkan"))
    }
}

// ── Media threads list (bahasan untuk satu anime) ───────────────────────────

@Composable
fun MediaThreadsScreen(
    mediaId: Int,
    title: String?,
    onBack: () -> Unit,
    onOpenThread: (Int) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val threads = remember { mutableStateListOf<AniThread>() }
    var page by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun load(reset: Boolean) {
        scope.launch {
            if (reset) {
                refreshing = true
                page = 1
                hasNext = true
            } else {
                loading = true
            }
            error = null
            try {
                val (list, next) = withContext(Dispatchers.IO) {
                    AniListThreads.mediaThreads(mediaId, page = if (reset) 1 else page)
                }
                if (reset) threads.clear()
                threads.addAll(list)
                hasNext = next
                if (next) page = (if (reset) 1 else page) + 1
            } catch (e: Exception) {
                error = e.toUserMessage()
                snackbar.show(scope, error ?: "Gagal memuat bahasan")
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(mediaId) { load(reset = true) }

    LaunchedEffect(listState, hasNext, loading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && hasNext && !loading && !refreshing) load(reset = false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(backdrop, blendColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        text = title?.takeIf { it.isNotBlank() }?.let { "Bahasan · $it" } ?: "Bahasan",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                    scrolledContainerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load(reset = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            when {
                loading && threads.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && threads.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error ?: "", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { load(reset = true) }) { Text("Coba lagi") }
                        }
                    }
                }
                threads.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Belum ada bahasan di AniList", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp + LocalBottomInset.current,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(threads, key = { it.id }) { thread ->
                            ThreadListItem(thread = thread, onClick = { onOpenThread(thread.id) })
                        }
                        if (loading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadListItem(thread: AniThread, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = thread.title.orEmpty().ifBlank { "(tanpa judul)" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stripHtml(thread.body).take(160).let { if (it.length >= 160) "$it…" else it },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            thread.user?.let { u ->
                AsyncImage(
                    model = u.avatar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(u.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            Text("${thread.viewCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            Text("${thread.likeCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            Text("${thread.replyCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(
            relativeTime(thread.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

// ── Thread details + comments ───────────────────────────────────────────────

@Composable
fun ThreadDetailsScreen(
    threadId: Int,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var thread by remember { mutableStateOf<AniThread?>(null) }
    val comments = remember { mutableStateListOf<AniThreadComment>() }
    var page by remember { mutableStateOf(1) }
    var hasNext by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var replyParent by remember { mutableStateOf<Int?>(null) }
    var posting by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(0) }
    var subscribed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun loadDetails() {
        scope.launch {
            try {
                val t = withContext(Dispatchers.IO) { AniListThreads.threadDetails(threadId) }
                thread = t
                if (t != null) {
                    liked = t.isLiked
                    likeCount = t.likeCount
                    subscribed = t.isSubscribed
                }
            } catch (e: Exception) {
                snackbar.show(scope, e.toUserMessage())
            }
        }
    }

    fun loadComments(reset: Boolean) {
        scope.launch {
            if (reset) {
                refreshing = true
                page = 1
                hasNext = true
            } else loading = true
            try {
                val (list, next) = withContext(Dispatchers.IO) {
                    AniListThreads.threadComments(threadId, page = if (reset) 1 else page)
                }
                if (reset) comments.clear()
                comments.addAll(list)
                hasNext = next
                if (next) page = (if (reset) 1 else page) + 1
            } catch (e: Exception) {
                snackbar.show(scope, e.toUserMessage())
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(threadId) {
        loadDetails()
        loadComments(reset = true)
    }

    LaunchedEffect(listState, hasNext, loading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && hasNext && !loading && !refreshing) loadComments(reset = false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(backdrop, blendColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        thread?.title?.takeIf { it.isNotBlank() } ?: "Bahasan",
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val next = !subscribed
                                    subscribed = withContext(Dispatchers.IO) {
                                        AniListThreads.subscribe(threadId, next)
                                    }
                                    snackbar.show(scope, if (subscribed) "Subscribe aktif" else "Subscribe mati")
                                } catch (e: Exception) {
                                    snackbar.show(scope, e.toUserMessage())
                                }
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            if (subscribed) Icons.Outlined.Notifications else Icons.Outlined.NotificationsNone,
                            contentDescription = "Subscribe",
                            tint = if (subscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                    scrolledContainerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                loadDetails()
                loadComments(reset = true)
            },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    item {
                        val t = thread
                        if (t == null && loading) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (t != null) {
                            ParentThreadBlock(
                                thread = t,
                                isLiked = liked,
                                likeCount = likeCount,
                                onLike = {
                                    scope.launch {
                                        try {
                                            val nowLiked = withContext(Dispatchers.IO) {
                                                AniListThreads.toggleLike(threadId, "THREAD")
                                            }
                                            liked = nowLiked
                                            likeCount = (likeCount + if (nowLiked) 1 else -1).coerceAtLeast(0)
                                        } catch (e: Exception) {
                                            snackbar.show(scope, e.toUserMessage())
                                        }
                                    }
                                },
                                onTranslate = { openTranslate(ctx, t.body.orEmpty()) },
                                onReply = {
                                    replyParent = null
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                    items(comments, key = { it.id }) { comment ->
                        CommentBlock(
                            comment = comment,
                            depth = 0,
                            onLike = { id ->
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            AniListThreads.toggleLike(id, "THREAD_COMMENT")
                                        }
                                    } catch (e: Exception) {
                                        snackbar.show(scope, e.toUserMessage())
                                    }
                                }
                            },
                            onTranslate = { text -> openTranslate(ctx, text) },
                            onReply = { parentId ->
                                replyParent = parentId
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                    if (loading && comments.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // Reply composer (like AniHyou publish comment)
                if (thread?.isLocked != true) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .padding(bottom = LocalBottomInset.current),
                    ) {
                        if (replyParent != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Membalas komentar #$replyParent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { replyParent = null }) { Text("Batal") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (replyParent == null) "Tulis komentar…" else "Tulis balasan…") },
                                maxLines = 4,
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                enabled = replyText.isNotBlank() && !posting,
                                onClick = {
                                    scope.launch {
                                        posting = true
                                        try {
                                            val parent = replyParent
                                            withContext(Dispatchers.IO) {
                                                AniListThreads.postComment(
                                                    threadId = if (parent == null) threadId else null,
                                                    parentCommentId = parent,
                                                    text = replyText.trim(),
                                                )
                                            }
                                            replyText = ""
                                            replyParent = null
                                            loadComments(reset = true)
                                            snackbar.show(scope, "Komentar terkirim")
                                        } catch (e: Exception) {
                                            snackbar.show(scope, e.toUserMessage())
                                        } finally {
                                            posting = false
                                        }
                                    }
                                },
                            ) {
                                Text(if (posting) "…" else "Kirim")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentThreadBlock(
    thread: AniThread,
    isLiked: Boolean,
    likeCount: Int,
    onLike: () -> Unit,
    onTranslate: () -> Unit,
    onReply: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            thread.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            relativeTime(thread.createdAt),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        MarkdownText(
            markdown = thread.body,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            thread.user?.let { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = u.avatar,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(u.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTranslate, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Translate, contentDescription = "Terjemahkan")
                }
                IconButton(onClick = onLike, shapes = IconButtonDefaults.shapes()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("$likeCount", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onReply, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = "Balas")
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("${thread.viewCount} dilihat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text("${thread.replyCount} balasan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun CommentBlock(
    comment: AniThreadComment,
    depth: Int,
    onLike: (Int) -> Unit,
    onTranslate: (String) -> Unit,
    onReply: (Int) -> Unit,
) {
    var isLiked by remember(comment.id, comment.isLiked) { mutableStateOf(comment.isLiked) }
    var likeCount by remember(comment.id, comment.likeCount) { mutableStateOf(comment.likeCount) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (16 + depth * 12).dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = comment.user?.avatar,
                contentDescription = null,
                modifier = Modifier.size(24.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                comment.user?.name.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                relativeTime(comment.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        MarkdownText(
            markdown = comment.comment,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onTranslate(comment.comment.orEmpty()) },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Outlined.Translate, contentDescription = "Terjemahkan", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    scope.launch {
                        isLiked = !isLiked
                        likeCount = (likeCount + if (isLiked) 1 else -1).coerceAtLeast(0)
                        onLike(comment.id)
                    }
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    if (likeCount > 0) {
                        Spacer(Modifier.width(2.dp))
                        Text("$likeCount", fontSize = 12.sp)
                    }
                }
            }
            if (comment.isLocked != true) {
                IconButton(
                    onClick = { onReply(comment.id) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = "Balas", modifier = Modifier.size(18.dp))
                }
            }
        }
        comment.childComments.forEach { child ->
            CommentBlock(
                comment = child,
                depth = (depth + 1).coerceAtMost(4),
                onLike = onLike,
                onTranslate = onTranslate,
                onReply = onReply,
            )
        }
    }
}
