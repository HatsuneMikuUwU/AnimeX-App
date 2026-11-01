@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.model.Episode
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.util.WindowBlurEffect
import com.uwu.animex.ui.viewmodel.PlayerViewModel

/**
 * Semua panel pilihan di player (kualitas, kecepatan, timer tidur, episode, setelan)
 * sekarang bottom sheet ala YouTube, bukan panel samping lagi.
 */
@Composable
private fun PlayerSheet(
    onDismiss: () -> Unit,
    hideSystemBars: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Cuma Hidden <-> Expanded: tinggi sheet ngikutin isi (maksimal ~70% layar),
    // jadi gak jadi setengah layar yang sempit pas landscape.
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
        KeepSystemBarsHidden(hideSystemBars)
        WindowBlurEffect()
        content()
    }
}

/**
 * Sheet jalan di window sendiri, jadi pas player lagi immersive status bar & nav bar
 * bisa muncul lagi. Sembunyikan lagi di window sheet-nya.
 */
@Composable
private fun KeepSystemBarsHidden(hide: Boolean) {
    if (!hide) return
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }
}

@Composable
private fun sheetListMaxHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

@Composable
internal fun PlayerOptionSheet(
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    hideSystemBars: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    PlayerSheet(onDismiss, hideSystemBars) {
        val listState = rememberLazyListState()
        LaunchedEffect(options.size, selected) {
            if (selected > 2) listState.scrollToItem(selected - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = sheetListMaxHeight()),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            itemsIndexed(options) { i, label ->
                val cs = MaterialTheme.colorScheme
                val current = i == selected
                val contentColor = if (current) cs.onPrimaryContainer else cs.onSurface
                SheetItemSurface(
                    shape = groupedItemShape(i, options.size),
                    onClick = { onSelect(i) },
                    container = if (current) cs.primaryContainer else cs.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (current) {
                                Icon(Icons.Outlined.Check, contentDescription = "Dipilih", tint = contentColor)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            label,
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EpisodeSheet(
    movieId: String,
    currentEpId: String,
    viewModel: PlayerViewModel,
    onPick: (Episode) -> Unit,
    onDismiss: () -> Unit,
    hideSystemBars: Boolean = false,
) {
    LaunchedEffect(movieId) {
        viewModel.loadEpisodes(movieId, force = false)
        viewModel.loadAniZip(movieId)
    }
    val state by viewModel.episodes.collectAsStateWithLifecycle()
    val aniZip by viewModel.aniZip.collectAsStateWithLifecycle()
    PlayerSheet(onDismiss, hideSystemBars) {
        when (val s = state) {
            UiState.Loading ->
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator()
                }
            is UiState.Error ->
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            s.msg,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TextButton(
                            onClick = hapticClick { viewModel.loadEpisodes(movieId, force = true) },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Coba lagi") }
                    }
                }
            is UiState.Ready -> {
                val list = s.value
                val listState = rememberLazyListState()
                LaunchedEffect(list, currentEpId) {
                    val i = list.indexOfFirst { it.id == currentEpId }
                    if (i > 0) listState.scrollToItem(i)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = sheetListMaxHeight()),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(list, key = { i, e -> e.id ?: "i$i" }) { _, ep ->
                        EpisodeRow(
                            ep = ep,
                            current = ep.id == currentEpId,
                            meta = ep.index?.trim()?.toIntOrNull()?.let { aniZip?.episodes?.get(it) },
                            onClick = { onPick(ep) },
                        )
                    }
                }
            }
        }
    }
}

internal class PlayerSettingItem(
    val icon: ImageVector,
    val label: String,
    val trailing: (@Composable RowScope.() -> Unit)? = null,
    val onClick: () -> Unit,
)

internal fun valueTrailing(text: String): @Composable RowScope.() -> Unit =
    {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

internal fun switchTrailing(checked: Boolean): @Composable RowScope.() -> Unit =
    {
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    }

/** Menu gear ala YouTube: satu sheet berisi semua setelan player. */
@Composable
internal fun PlayerSettingsSheet(
    items: List<PlayerSettingItem>,
    onDismiss: () -> Unit,
    hideSystemBars: Boolean = false,
) {
    PlayerSheet(onDismiss, hideSystemBars) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = sheetListMaxHeight()),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            itemsIndexed(items) { i, item ->
                SheetOptionItem(
                    index = i,
                    count = items.size,
                    icon = item.icon,
                    label = item.label,
                    selected = false,
                    onClick = item.onClick,
                    trailing = item.trailing,
                )
            }
        }
    }
}
