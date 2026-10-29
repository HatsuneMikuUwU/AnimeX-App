@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.SheetOptionItem
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

/** Teks tema MAL (`#1: "Judul" by Artis (eps 1-12)`) jadi kata kunci pencarian: tanpa nomor, tanda kutip, dan episode. */
internal fun String.toThemeQuery(): String =
    replace("\"", "")
        .replaceFirst(Regex("^\\s*#?\\w+:"), "")
        .replace(Regex("\\(eps?.*\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Kartu daftar opening / ending (gaya sama dengan kartu distribusi status); tap lagu buat cari di layanan musik. */
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
    Column(
        Modifier
            .padding(horizontal = inset)
            .fillMaxWidth()
            .clip(InfoCardShape)
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .animateContentSize(),
    ) {
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
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "Lebih sedikit" else "Lihat semua (${themes.size})")
            }
        }
    }
}

/** Pilihan layanan musik buat cari [songText]. */
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
