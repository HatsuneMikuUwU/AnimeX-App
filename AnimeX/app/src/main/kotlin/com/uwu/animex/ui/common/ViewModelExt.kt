package com.uwu.animex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.di.AppContainer

/**
 * Bikin ViewModel dengan dependency dari [AppContainer] tanpa Factory manual per-VM.
 * [key] wajib diisi kalau satu screen bisa punya beberapa instance VM yang sama (mis. per-id / per-query).
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline build: (AppContainer) -> VM,
): VM {
    val appContext = LocalContext.current.applicationContext
    val container = remember(appContext) { AppContainer.get(appContext) }
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
        },
    )
}
