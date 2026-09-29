package com.uwu.animex.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Pengganti Toast: tampilkan Snackbar Material, pesan lama langsung diganti (tidak antre). */
fun SnackbarHostState.show(scope: CoroutineScope, message: String) {
    currentSnackbarData?.dismiss()
    scope.launch { showSnackbar(message, duration = SnackbarDuration.Short) }
}
