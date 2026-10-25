@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlin.math.abs

internal fun List<Server>.bestDirect(): Server? {
    val direct = filter { it.isDirect && !it.link.isNullOrBlank() }
    val preferred = PlayerPrefs.quality
    if (preferred <= 0) return direct.maxByOrNull { it.qualityValue }
    return direct
        .filter { it.qualityValue > 0 }
        .minWithOrNull(compareBy<Server>({ abs(it.qualityValue - preferred) }, { it.qualityValue }))
        ?: direct.firstOrNull()
}

internal fun List<Server>.browserLink(): String? =
    (firstOrNull { !it.isDirect && !it.link.isNullOrBlank() } ?: bestDirect())?.link

@Composable
internal fun EpisodeActionSheet(
    episode: Episode,
    watched: Boolean,
    onDismiss: () -> Unit,
    onBrowser: () -> Unit,
    onExternal: () -> Unit,
    onToggleWatched: () -> Unit,
    onWatchedUpTo: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val title =
        listOfNotNull(
            "Episode ${episode.index.orEmpty()}".trim(),
            episode.title?.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            val upTo = episode.index?.trim()?.toDoubleOrNull() != null
            val actions =
                buildList {
                    add(Triple(Icons.Outlined.OpenInBrowser, "Putar di browser", onBrowser))
                    add(Triple(Icons.AutoMirrored.Outlined.OpenInNew, "Putar di player eksternal", onExternal))
                    add(
                        Triple(
                            if (watched) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (watched) "Tandai belum ditonton" else "Tandai telah ditonton",
                            onToggleWatched,
                        ),
                    )
                    if (upTo) {
                        add(Triple(Icons.Outlined.DoneAll, "Tandai telah ditonton sampai episode ini", onWatchedUpTo))
                    }
                }
            actions.forEachIndexed { index, (icon, label, action) ->
                SheetOptionItem(
                    index = index,
                    count = actions.size,
                    icon = icon,
                    label = label,
                    selected = false,
                    onClick = action,
                )
            }
        }
    }
}
