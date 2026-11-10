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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteSweep
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
import com.uwu.animex.data.local.CuplixSaved
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
fun CuplixSavedScreen(
    onBack: () -> Unit,
    onPlay: (Cuplix) -> Unit,
) {
    val ctx = LocalContext.current
    val saved by CuplixSaved.clips.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        "Cuplix Tersimpan",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = if (saved.isNotEmpty()) Icons.Outlined.DeleteSweep else null,
                trailingDescription = "Hapus semua tersimpan",
                onTrailingClick = { confirmClear = true },
            )
        },
        containerColor = screenContainerColor(),
    ) { pad ->
        BlurContentBox(pad, backdrop) {
            if (saved.isEmpty()) {
                AnimatedEmptyState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "Belum ada Cuplix tersimpan",
                    message = "Tap ikon simpan di Cuplix buat nyimpen yang kamu suka.",
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
                    items(saved, key = { cuplixKey(it) }, contentType = { "cuplix-card" }) { clip ->
                        CuplixCard(clip = clip, onPlay = onPlay, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (confirmClear) {
        CuplixClearDialog(
            title = "Hapus semua tersimpan?",
            message = "Semua Cuplix yang kamu simpan bakal dihapus dari daftar ini. Riwayat tontonan nggak ikut terhapus.",
            onConfirm = {
                confirmClear = false
                CuplixSaved.clear()
                Toast.makeText(ctx, "Semua Cuplix tersimpan dihapus", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
