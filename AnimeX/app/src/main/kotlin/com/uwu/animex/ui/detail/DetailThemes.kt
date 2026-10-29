@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.util.WindowBlurEffect

private const val THEMES_LIMIT = 5

private enum class MusicService(
    val label: String,
    val icon: ImageVector,
    val searchUrl: String,
) {
    YouTube("YouTube", Icons.Outlined.PlayCircleOutline, "https://www.youtube.com/results?search_query="),
    YouTubeMusic("YouTube Music", Icons.Outlined.MusicNote, "https://music.youtube.com/search?q="),
    Spotify("Spotify", Icons.Outlined.MusicNote, "https://open.spotify.com/search/"),
    AppleMusic("Apple Music", Icons.Outlined.MusicNote, "https://music.apple.com/search?term="),
    Deezer("Deezer", Icons.Outlined.MusicNote, "https://www.deezer.com/search/"),
}

internal fun String.toThemeQuery(): String =
    replace("\"", "")
        .replaceFirst(Regex("^\\s*#?\\w+:"), "")
        .replace(Regex("\\(eps?.*\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

@Composable
internal fun ThemeListCard(
    title: String,
    icon: ImageVector,
    themes: List<String>,
    inset: Dp = 16.dp,
    onSelect: (String) -> Unit,
) {
    if (themes.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val shown = if (expanded) themes else themes.take(THEMES_LIMIT)
    Surface(
        shape = InfoCardShape,
        color = cs.surfaceContainerHigh,
        modifier =
            Modifier
                .padding(horizontal = inset)
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            CardLabel(title, icon)
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEach { theme ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceContainerHighest)
                            .clickable { onSelect(theme) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            theme,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (themes.size > THEMES_LIMIT) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "themes-chevron",
                )
                Surface(
                    onClick = hapticClick { expanded = !expanded },
                    shape = CircleShape,
                    color = cs.secondaryContainer,
                    contentColor = cs.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (expanded) "Tutup" else "Selengkapnya (${themes.size})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "Tutup daftar $title" else "Lihat semua $title",
                            modifier = Modifier.padding(start = 2.dp).size(18.dp).rotate(rotation),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MusicServiceSheet(
    songText: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val query = Uri.encode(songText.toThemeQuery())
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            val services = MusicService.entries
            services.forEachIndexed { index, service ->
                SheetOptionItem(
                    index = index,
                    count = services.size,
                    icon = service.icon,
                    label = service.label,
                    selected = false,
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(service.searchUrl + query))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        onDismiss()
                    },
                )
            }
        }
    }
}
