package com.uwu.animex.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/**
 * Swipe kiri/kanan antar tab ala Telegram: halaman ikut digeser jari.
 *
 * - Tap di tab bar -> pager animasi ke halaman tujuan.
 * - Swipe di pager -> [onTabChange] dipanggil setelah halaman settle.
 * - Scroll horizontal di dalam konten (mis. carousel) tetap diprioritaskan;
 *   begitu mentok di ujung, gesture diteruskan ke pager.
 */
@Composable
fun SwipeTabPager(
    tab: Int,
    count: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    val safeCount = count.coerceAtLeast(1)
    val pagerState =
        rememberPagerState(initialPage = tab.coerceIn(0, safeCount - 1)) { safeCount }
    val currentTab by rememberUpdatedState(tab)
    val currentOnTabChange by rememberUpdatedState(onTabChange)

    LaunchedEffect(tab, safeCount) {
        val target = tab.coerceIn(0, safeCount - 1)
        if (pagerState.targetPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != currentTab) currentOnTabChange(page)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        key = { it },
    ) { page -> content(page) }
}
