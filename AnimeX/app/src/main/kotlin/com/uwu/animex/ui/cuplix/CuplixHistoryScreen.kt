@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.cuplix

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.local.CuplixWatched
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.CuplixCard
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.theme.rememberBlurBackdrop

@Composable
fun CuplixHistoryScreen(
    onBack: () -> Unit,
    onPlay: (Cuplix) -> Unit,
) {
    val ctx = LocalContext.current
    val history by CuplixWatched.clips.collectAsStateWithLifecycle()
    val hideWatched by CuplixWatched.hideWatched.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        "Riwayat Cuplix",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = if (history.isNotEmpty()) Icons.Outlined.DeleteSweep else null,
                trailingDescription = "Hapus semua riwayat",
                onTrailingClick = { confirmClear = true },
            )
        },
        containerColor = screenContainerColor(),
    ) { pad ->
        BlurContentBox(pad, backdrop) {
            if (history.isEmpty()) {
                AnimatedEmptyState(
                    icon = Icons.Outlined.History,
                    title = "Belum ada riwayat Cuplix",
                    message = "Cuplix yang kamu tonton bakal muncul di sini.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp + LocalTopInset.current,
                            bottom = 8.dp + LocalBottomInset.current,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(history, key = { cuplixKey(it) }, contentType = { "cuplix-card" }) { clip ->
                        CuplixCard(clip = clip, onPlay = onPlay, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (confirmClear) {
        CuplixClearDialog(
            title = "Hapus semua riwayat?",
            message =
                if (hideWatched) {
                    "Semua Cuplix yang sudah ditonton bakal dihapus dari riwayat dan muncul lagi di " +
                        "beranda, daftar Cuplix, dan feed. Cuplix tersimpan nggak ikut terhapus."
                } else {
                    "Semua Cuplix yang sudah ditonton bakal dihapus dari riwayat. " +
                        "Cuplix tersimpan nggak ikut terhapus."
                },
            onConfirm = {
                confirmClear = false
                CuplixWatched.clear()
                Toast.makeText(ctx, "Riwayat Cuplix dihapus", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
