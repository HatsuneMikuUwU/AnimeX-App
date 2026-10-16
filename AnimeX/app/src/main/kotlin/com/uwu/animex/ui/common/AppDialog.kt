@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.theme.UiScaled
import com.uwu.animex.ui.util.WindowBlurEffect

@Composable
fun AppDialog(
    icon: ImageVector,
    title: String,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier =
        Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 560.dp),
    dismissButton: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { UiScaled { confirmButton() } },
        modifier = modifier,
        dismissButton = dismissButton?.let(::scaledSlot),
        icon = { UiScaled { Icon(icon, contentDescription = null) } },
        title = {
            WindowBlurEffect()
            UiScaled { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        },
        text = text?.let(::scaledSlot),
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun DialogOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val onHapticClick = hapticClick(block = onClick)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onHapticClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun DialogCheckRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val onHapticClick = hapticClick(block = onClick)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onHapticClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun DialogCancelButton(
    label: String = "Gak usah deh",
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = hapticClick(block = onClick),
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Text(label)
    }
}

@Composable
fun DialogConfirmButton(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = hapticClick(block = onClick),
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Text(label)
    }
}

@Composable
fun DialogDestructiveButton(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = hapticClick(block = onClick),
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Text(label)
    }
}
