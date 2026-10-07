package com.uwu.animex.ui.character

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.AnimeCharacter
import com.uwu.animex.data.api.CharacterRole
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset

@Composable
fun CharacterListTab(
    characters: List<AnimeCharacter>,
    loading: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    when {
        loading && characters.isEmpty() ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CenterLoading()
            }
        characters.isEmpty() ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Karakternya gak ketemu",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        else ->
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp + LocalTopInset.current,
                        bottom = 8.dp + LocalBottomInset.current,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(characters, key = { it.id ?: it.character.name }) { item ->
                    CharacterListRow(item)
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
    }
}

@Composable
private fun CharacterListRow(item: AnimeCharacter) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh
        AsyncImage(
            model = item.character.image,
            contentDescription = item.character.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(placeholder),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.character.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            roleLabel(item.role)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item.voiceActor?.name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item.voiceActor?.image?.let { va ->
            AsyncImage(
                model = va,
                contentDescription = item.voiceActor.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(placeholder),
            )
        }
    }
}

private fun roleLabel(r: CharacterRole?): String? =
    when (r) {
        CharacterRole.MAIN -> "Utama"
        CharacterRole.SUPPORTING -> "Pendukung"
        CharacterRole.BACKGROUND -> "Figuran"
        null -> null
    }
