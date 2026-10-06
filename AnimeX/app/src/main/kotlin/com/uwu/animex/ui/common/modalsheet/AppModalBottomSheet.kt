package com.uwu.animex.ui.common.modalsheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Modal bottom sheet gaya ImageToolbox: dirender di popup fullscreen (di atas system UI), lalu
 * sheet-nya dikasih [statusBarsPadding] jadi tinggi maksimalnya mentok tepat di bawah status bar
 * dan sudut atas yang rounded tetap kelihatan.
 *
 * Sheet selalu langsung terbuka penuh (tanpa tahap setengah). Set [visible] ke false buat nutup
 * dengan animasi; [onDismissed] dipanggil SETELAH sheet benar-benar tertutup, baik karena [visible]
 * jadi false, swipe ke bawah, tap scrim, maupun tombol back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissed: () -> Unit,
    visible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
    )

    var opened by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    fun dismissOnce() {
        if (!dismissed) {
            dismissed = true
            currentOnDismissed()
        }
    }
    fun requestClose() {
        scope.launch { sheetState.hide() }
    }

    LaunchedEffect(visible) {
        if (visible) {
            sheetState.show()
        } else {
            sheetState.hide()
            dismissOnce()
        }
    }

    // Ketutup lewat swipe / scrim / back -> state balik ke Hidden setelah pernah kebuka.
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
            if (opened) dismissOnce()
        } else {
            opened = true
        }
    }

    BackHandler(enabled = visible && !dismissed) { requestClose() }

    val containerColor = BottomSheetDefaults.ContainerColor
    val contentColor = contentColorFor(containerColor)

    ModalSheet(
        sheetState = sheetState,
        onDismiss = { requestClose() },
        sheetModifier = Modifier.statusBarsPadding(),
        nestedScrollEnabled = true,
        dragHandle = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) { BottomSheetDefaults.DragHandle() }
        },
        shape = BottomSheetDefaults.ExpandedShape,
        elevation = 0.dp,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        // Isi sheet nggak boleh ketutup navigation bar (popup fullscreen nggak ngurus ini sendiri).
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            content()
        }
    }
}
