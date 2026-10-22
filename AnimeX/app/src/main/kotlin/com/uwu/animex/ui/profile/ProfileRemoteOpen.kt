package com.uwu.animex.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.TitleMatcher
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import kotlinx.coroutines.launch

/**
 * Membuka detail AnimeX dari sebuah anime di AniList/MAL. Id sumber dicocokkan ke sumber AnimeX
 * lewat judul (sekali saja, hasilnya disimpan lewat [link]), persis seperti di tab Riwayat.
 */
internal class RemoteOpener(
    val open: (remoteId: Int, titles: List<String>) -> Unit,
    val dialog: @Composable () -> Unit,
)

private class PickState(
    val remoteId: Int,
    val title: String,
    val candidates: List<Movie>,
)

@Composable
internal fun rememberRemoteOpener(
    onOpenDetail: (String) -> Unit,
    movieIdFor: (Int) -> String?,
    link: (movieId: String, remoteId: Int) -> Unit,
    snackbar: SnackbarHostState,
): RemoteOpener {
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<PickState?>(null) }

    val open: (Int, List<String>) -> Unit = open@{ remoteId, titles ->
        movieIdFor(remoteId)?.let {
            onOpenDetail(it)
            return@open
        }
        val title = titles.firstOrNull().orEmpty()
        if (resolving != null || title.isBlank()) return@open
        resolving = remoteId
        scope.launch {
            try {
                val found = TitleMatcher.find(titles)
                val bestId = found.best?.id
                when {
                    bestId != null -> {
                        link(bestId, remoteId)
                        onOpenDetail(bestId)
                    }
                    found.ranked.isNotEmpty() -> picking = PickState(remoteId, title, found.ranked)
                    else -> snackbar.show(scope, "\"$title\" gak ketemu di sumber AnimeX")
                }
            } catch (e: Exception) {
                snackbar.show(scope, "Gagal nyari: ${e.toUserMessage()}")
            } finally {
                resolving = null
            }
        }
    }

    val dialog: @Composable () -> Unit = {
        picking?.let { pick ->
            AppDialog(
                icon = Icons.Outlined.Link,
                onDismiss = { picking = null },
                title = "Pilih yang paling pas",
                text = {
                    Column {
                        Text(
                            pick.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        pick.candidates.take(8).forEach { m ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClick = hapticClick {
                                            val id = m.id ?: return@hapticClick
                                            link(id, pick.remoteId)
                                            picking = null
                                            onOpenDetail(id)
                                        },
                                    ).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Poster(m.image_poster, Modifier.size(40.dp, 56.dp), 8.dp)
                                Text(
                                    m.title.orEmpty(),
                                    modifier = Modifier.padding(start = 12.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                confirmButton = { DialogCancelButton("Gak usah deh") { picking = null } },
            )
        }
    }
    return remember(onOpenDetail, movieIdFor, link) { RemoteOpener(open, dialog) }
}
