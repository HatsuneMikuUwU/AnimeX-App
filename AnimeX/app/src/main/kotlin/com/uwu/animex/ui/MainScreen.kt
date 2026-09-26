@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private data class NavItem(val label: String, val icon: ImageVector)

private val NAV = listOf(
    NavItem("Home", Icons.Filled.Home),
    NavItem("Jadwal", Icons.Filled.DateRange),
    NavItem("Cari", Icons.Filled.Search),
    NavItem("Setelan", Icons.Filled.Settings),
)

@Composable
fun MainScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpenCategory: () -> Unit = {},
    onOpenYear: () -> Unit = {},
    onOpenTheme: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                NAV.forEachIndexed { i, item ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(onOpen, onMore)
                1 -> ScheduleScreen(onOpen)
                2 -> SearchScreen(
                    onOpen = onOpen,
                    onFilter = onFilter,
                    onOpenCategory = onOpenCategory,
                    onOpenYear = onOpenYear,
                )
                else -> SettingsScreen(onOpenTheme = onOpenTheme)
            }
        }
    }
}
