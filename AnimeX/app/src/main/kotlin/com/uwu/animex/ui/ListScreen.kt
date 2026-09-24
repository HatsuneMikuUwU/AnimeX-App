@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api

private val TITLES = mapOf(
    "update" to "Episode Baru", "hot" to "Sedang Hangat", "new" to "Judul Baru",
    "random" to "Jas Por Yu", "popular" to "Populer",
)

@Composable
fun ListScreen(key: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by rememberLoad(key) {
        if (key == "update") Api.newEpisodes() else Api.homeMovies(key)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLES[key] ?: "Daftar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (val s = state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                is UiState.Ready -> MovieGrid(s.value, onOpen, bottomPad = 16.dp)
            }
        }
    }
}
