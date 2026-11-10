package com.uwu.animex.ui.cuplix

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton

@Composable
internal fun CuplixClearDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        icon = Icons.Outlined.DeleteSweep,
        onDismiss = onDismiss,
        title = title,
        text = { Text(message) },
        confirmButton = { DialogDestructiveButton("Hapus", onClick = onConfirm) },
        dismissButton = { DialogCancelButton(onClick = onDismiss) },
    )
}
