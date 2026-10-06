package com.uwu.animex.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.ui.theme.UI_SCALE_DEFAULT
import com.uwu.animex.ui.theme.UI_SCALE_MAX
import com.uwu.animex.ui.theme.UI_SCALE_MIN
import com.uwu.animex.ui.theme.UI_SCALE_SLIDER_STEPS
import com.uwu.animex.ui.theme.coerceToUiScale
import com.uwu.animex.ui.theme.systemDensityDpi
import com.uwu.animex.ui.theme.toEffectiveDpi
import com.uwu.animex.ui.theme.toUiScalePercent

/**
 * Bottom sheet DPI kustom. Tampilannya tetap yang lama (nilai dpi besar, slider, Ikut sistem / Terapkan),
 * sedangkan nilainya disimpan sebagai skala antarmuka seperti di Morphe Manager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiScaleSheet(
    currentScale: Float,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val appliedScale = currentScale.coerceToUiScale()
    var draft by remember { mutableFloatStateOf(appliedScale) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("DPI kustom", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Text(
                "Atur kerapatan tampilan aplikasi. DPI lebih kecil membuat elemen lebih kecil dan layar lebih lega, " +
                    "DPI lebih besar membuat elemen lebih besar. Hanya berlaku di AnimeX, tidak mengubah pengaturan sistem.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Text(
                "${draft.toEffectiveDpi()} dpi",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = cs.primary,
            )
            Text(
                "${draft.toUiScalePercent()}% dari DPI sistem (${systemDensityDpi()} dpi)",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
            Slider(
                value = draft,
                // Dibulatkan ke anak tangga yang valid, sama seperti Morphe
                onValueChange = { draft = it.coerceToUiScale() },
                valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                steps = UI_SCALE_SLIDER_STEPS,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onApply(UI_SCALE_DEFAULT)
                        onDismiss()
                    },
                    enabled = appliedScale != UI_SCALE_DEFAULT,
                ) { Text("Ikut sistem") }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                ) { Text("Terapkan") }
            }
        }
    }
}
