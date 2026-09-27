@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.data.History
import com.uwu.animex.data.SettingsPrefs
import com.uwu.animex.data.ThemeMode

private data class SettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBg: Color,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    onOpenUiCustom: () -> Unit = {},
) {
    val themeLabel = when (SettingsPrefs.themeMode) {
        ThemeMode.SYSTEM -> "Sistem"
        ThemeMode.LIGHT -> "Terang"
        ThemeMode.DARK -> "Gelap"
    }
    val accentLabel = SettingsPrefs.accentColor.label()

    val items = listOf(
        SettingsItem(
            title = "Kustomisasi UI",
            subtitle = "Material · $themeLabel · $accentLabel",
            icon = Icons.Filled.Palette,
            iconBg = Color(0xFF7B61FF),
            onClick = onOpenUiCustom,
        ),
        SettingsItem(
            title = "Data & Penyimpanan",
            subtitle = "Riwayat tonton · ${History.items.size} item",
            icon = Icons.Filled.Storage,
            iconBg = Color(0xFF4A90E2),
            onClick = { },
        ),
        SettingsItem(
            title = "Hapus Riwayat",
            subtitle = "Hapus semua progres lanjut nonton",
            icon = Icons.Filled.Delete,
            iconBg = Color(0xFFE53935),
            onClick = {
                History.items.mapNotNull { it.id }.forEach(History::remove)
            },
        ),
        SettingsItem(
            title = "Tentang",
            subtitle = "AnimeX · versi 1.0.4",
            icon = Icons.Filled.Info,
            iconBg = Color(0xFF26A69A),
            onClick = { },
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Setelan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
            )
        }
        items.forEach { item ->
            item {
                SettingsCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    iconBg = item.iconBg,
                    onClick = item.onClick,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
