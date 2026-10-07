package com.uwu.animex.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun SnackbarHostState.show(
    scope: CoroutineScope,
    message: String,
) {
    currentSnackbarData?.dismiss()
    scope.launch { showSnackbar(message, duration = SnackbarDuration.Short) }
}
