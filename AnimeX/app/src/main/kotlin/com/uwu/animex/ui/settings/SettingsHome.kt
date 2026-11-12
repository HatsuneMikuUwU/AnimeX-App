@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.CuplixPrefs
import com.uwu.animex.data.local.HomeSection
import com.uwu.animex.data.local.HomeSectionPrefs
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.ShapedIconBadge
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.home.icon
import com.uwu.animex.ui.util.WindowBlurEffect

@Composable
internal fun HomeGroup() {
    val cs = MaterialTheme.colorScheme
    val layout by HomeSectionPrefs.layout.collectAsStateWithLifecycle()
    val cuplixEnabled by CuplixPrefs.enabled.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    SettingGroup(icon = Icons.Outlined.Dashboard, title = "Beranda", initiallyExpanded = false) {
        PrefItem(
            icon = Icons.Outlined.ViewAgenda,
            title = "Atur daftar beranda",
            subtitle =
                when {
                    layout.isDefault -> "Pindahkan urutan daftar di beranda"
                    else -> "Urutan daftar sudah diubah"
                },
            shape = itemShape(0, 2),
            container = itemContainer(),
            content = cs.onSurface,
            onClick = { showSheet = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
        )
        SwitchItem(
            icon = Icons.Outlined.PlayCircleOutline,
            title = "Nonaktifkan Cuplix",
            subtitle = "Sembunyikan Cuplix dari beranda dan matikan putar klip pendeknya",
            shape = itemShape(1, 2),
            checked = !cuplixEnabled,
            enabled = true,
            onChange = { CuplixPrefs.setEnabled(!it) },
        )
    }

    if (showSheet) {
        HomeSectionSheet(onDismiss = { showSheet = false })
    }
}

@Composable
private fun HomeSectionSheet(onDismiss: () -> Unit) {
    val layout by HomeSectionPrefs.layout.collectAsStateWithLifecycle()
    val cuplixEnabled by CuplixPrefs.enabled.collectAsStateWithLifecycle()
    val sections = layout.order.filter { it != HomeSection.Cuplix || cuplixEnabled }
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )

    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        LazyColumn(Modifier.weight(1f, fill = false)) {
            itemsIndexed(sections, key = { _, section -> section.key }) { index, section ->
                HomeSectionRow(
                    section = section,
                    index = index,
                    sections = sections,
                    modifier = animateItemExpressive(),
                )
            }
            if (!layout.isDefault) {
                item(key = "reset") {
                    Column(animateItemExpressive().padding(top = 12.dp)) {
                        SheetOptionItem(
                            index = 0,
                            count = 1,
                            icon = Icons.Outlined.RestartAlt,
                            label = "Atur ulang ke bawaan",
                            selected = false,
                            onClick = HomeSectionPrefs::reset,
                        )
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    index: Int,
    sections: List<HomeSection>,
    modifier: Modifier = Modifier,
) {
    val count = sections.size
    val cs = MaterialTheme.colorScheme
    val moveUp = hapticClick { HomeSectionPrefs.move(section, -1, sections) }
    val moveDown = hapticClick { HomeSectionPrefs.move(section, 1, sections) }
    SheetItemSurface(
        shape = groupedItemShape(index, count),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapedIconBadge(icon = section.icon)
            Text(
                section.title,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = cs.onSurface,
            )
            IconButton(onClick = moveUp, enabled = index > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Naikkan ${section.title}")
            }
            IconButton(onClick = moveDown, enabled = index < count - 1) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Turunkan ${section.title}")
            }
        }
    }
}
