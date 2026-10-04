package com.uwu.animex.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.app.App

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val msg: String) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterLoading() = Box(Modifier.fillMaxSize().padding(top = LocalTopInset.current), Alignment.Center) {
    LoadingIndicator()
}

@Composable
fun CenterText(text: String, color: Color = Color.Unspecified) =
    Box(Modifier.fillMaxSize().padding(top = LocalTopInset.current).padding(24.dp), Alignment.Center) { Text(text, color = color) }

/** Repository untuk composable yang cuma butuh util stateless (mis. `absUrl`) tanpa ViewModel sendiri. */
val LocalAnimeRepository = staticCompositionLocalOf<com.uwu.animex.data.repository.AnimeRepository> {
    error("LocalAnimeRepository belum di-provide. Bungkus App() di MainActivity.")
}

/** Tinggi search bar yang floating di atas konten tab; dipakai sebagai padding atas list. */
val LocalTopInset = compositionLocalOf { 0.dp }

/** Padding atas list: di bawah search bar floating kalau ada, kalau tidak 8dp biasa. */
@Composable
fun contentTopPadding(): Dp {
    val inset = LocalTopInset.current
    return if (inset > 0.dp) inset + 16.dp else 8.dp
}

/** True kalau layar lagi landscape. Dipakai buat ganti bottom bar jadi navigation rail. */
@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
