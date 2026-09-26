@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // No Scaffold here on purpose: Scaffold's bottomBar slot reserves layout space and
    // pushes content up above it. We want the nav pill to float ON TOP of the content
    // instead, so scrolled content is visible (and scrolls) right through/behind it,
    // all the way down to the system navigation bar.
    Box(Modifier.fillMaxSize()) {
        // Scaffold used to add the top status-bar inset for free via its innerPadding;
        // since we dropped it, the content needs its own statusBarsPadding so the banner/
        // list doesn't render underneath the status bar. The bottom nav keeps its own
        // navigationBarsPadding below, independently, so it still floats over the content.
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            when (tab) {
                0 -> HomeScreen(onOpen, onMore)
                1 -> ScheduleScreen(onOpen)
                2 -> SearchScreen(
                    onOpen = onOpen,
                    onFilter = onFilter,
                    onOpenCategory = onOpenCategory,
                    onOpenYear = onOpenYear,
                )
                else -> SettingsScreen()
            }
        }
        GlassBottomNav(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Extra bottom space that scrollable tab content (Home/Jadwal/Cari lists) should reserve
 * in their contentPadding so their last items aren't hidden underneath the floating pill,
 * while everything above still scrolls freely behind/through it.
 */
val FloatingNavClearance = 96.dp

/** Floating segmented pill bottom nav: the selected item expands with a label, the rest shrink to an icon. */
@Composable
private fun GlassBottomNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        // One solid, fully-opaque bar behind everything (not per-item alpha) so the pill
        // stays clearly readable no matter what's scrolling behind it.
        Surface(
            modifier = Modifier.widthIn(max = 448.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NAV.forEachIndexed { i, item ->
                    val isSelected = selected == i
                    GlassNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onSelect(i) },
                        modifier = if (isSelected) Modifier.weight(1f) else Modifier.width(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassNavItem(item: NavItem, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Unselected items sit directly on the solid bar Surface above (no background of their
    // own), so they read as opaque icons rather than a see-through box over the content.
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.icon, contentDescription = if (isSelected) null else item.label, modifier = Modifier.height(22.dp))
            AnimatedVisibility(
                visible = isSelected,
                enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                exit = shrinkHorizontally(tween(220)) + fadeOut(tween(220)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.label,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
