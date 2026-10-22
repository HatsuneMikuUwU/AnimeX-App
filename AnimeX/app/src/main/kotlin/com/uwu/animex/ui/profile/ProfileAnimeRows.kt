package com.uwu.animex.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.PortraitCard
import com.uwu.animex.ui.common.SectionHeader

/** Satu anime dari AniList/MAL yang ditampilkan dengan kartu yang sama seperti di Home. */
internal class RemoteAnimeCard(
    val remoteId: Int,
    val titles: List<String>,
    val movie: Movie,
    val chip: String? = null,
    val stat: String? = null,
    val statIsPopularity: Boolean = false,
)

@Composable
internal fun RemoteAnimeRow(
    list: List<RemoteAnimeCard>,
    onOpen: (RemoteAnimeCard) -> Unit,
) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val scale = settings.homeCardScale
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.remoteId }, contentType = { "remote-portrait" }) { card ->
            PortraitCard(
                m = card.movie,
                modifier = Modifier.width(105.dp * scale),
                labelOverride = card.chip,
                statLabel = card.stat,
                statBadge =
                    if (card.statIsPopularity) {
                        {
                            Icon(
                                Icons.Outlined.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        null
                    },
            ) { onOpen(card) }
        }
    }
}

internal class ExploreSectionModel(
    val title: String,
    val icon: ImageVector,
    val items: List<RemoteAnimeCard>,
)

/** Daftar section bergaya Home (SectionHeader + baris PortraitCard) untuk tab Eksplorasi. */
@Composable
internal fun ExploreSectionList(
    sections: List<ExploreSectionModel>,
    onOpen: (remoteId: Int, titles: List<String>) -> Unit,
) {
    Column {
        sections.forEachIndexed { index, section ->
            SectionHeader(
                title = section.title,
                onMore = null,
                topPadding = if (index == 0) 0.dp else 18.dp,
                icon = section.icon,
            )
            RemoteAnimeRow(section.items) { card -> onOpen(card.remoteId, card.titles) }
        }
    }
}
