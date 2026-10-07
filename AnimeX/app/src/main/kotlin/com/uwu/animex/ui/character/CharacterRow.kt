package com.uwu.animex.ui.character

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.AnimeCharacter
import com.uwu.animex.data.api.CharacterRole

@Composable
fun CharacterRow(
    characters: List<AnimeCharacter>,
    modifier: Modifier = Modifier,
) {
    if (characters.isEmpty()) return
    Column(modifier) {
        Text(
            "Karakter",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(characters, key = { i, c -> "$i-${c.character.name}" }) { _, c -> CharacterItem(c) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterItem(item: AnimeCharacter) {
    val ctx = LocalContext.current
    val hasVa = item.voiceActor != null
    var inverted by remember(item) { mutableStateOf(false) }

    val vaImage = item.voiceActor?.image?.takeIf { it.isNotBlank() }
    val showVa = inverted && vaImage != null
    val mainImg = if (showVa) vaImage else item.character.image
    val backImg = if (showVa) item.character.image else item.voiceActor?.image
    val mainName = item.character.name

    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(inverted) {
        scale.snapTo(0.8f)
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }

    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val placeholder = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier =
            Modifier
                .width(100.dp)
                .padding(5.dp)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = { inverted = !inverted },
                    onLongClick = {
                        val i =
                            Intent(Intent.ACTION_WEB_SEARCH)
                                .putExtra(SearchManager.QUERY, mainName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            ctx.startActivity(i)
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 80.dp, height = 75.dp)) {
            if (hasVa && !backImg.isNullOrBlank()) {
                AsyncImage(
                    model = backImg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(70.dp)
                            .alpha(0.2f)
                            .clip(CircleShape)
                            .background(placeholder)
                            .border(1.dp, outline, CircleShape),
                )
            }
            AsyncImage(
                model = mainImg,
                contentDescription = mainName,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .scale(scale.value)
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(placeholder)
                        .border(1.dp, outline, CircleShape),
            )
        }

        Column(
            Modifier.padding(vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                mainName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.voiceActor?.let {
                Text(
                    it.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            roleLabel(item.role)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
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
