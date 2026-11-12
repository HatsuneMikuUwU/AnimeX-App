package com.uwu.animex.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import com.uwu.animex.data.local.HomeSection

val HomeSection.icon: ImageVector
    get() =
        when (this) {
            HomeSection.Cuplix -> Icons.Outlined.PlayCircleOutline
            HomeSection.ContinueWatching -> Icons.Outlined.History
            HomeSection.NewEpisodes -> Icons.Outlined.NewReleases
            HomeSection.Hot -> Icons.Outlined.LocalFireDepartment
            HomeSection.NewTitles -> Icons.Outlined.AutoAwesome
            HomeSection.Today -> Icons.Outlined.Today
            HomeSection.Waiting -> Icons.Outlined.HourglassTop
            HomeSection.Popular -> Icons.Outlined.Leaderboard
            HomeSection.WibuTrending -> Icons.Outlined.LocalFireDepartment
            HomeSection.WibuTopAllTime -> Icons.Outlined.Leaderboard
            HomeSection.WibuRandom -> Icons.Outlined.Shuffle
            HomeSection.WibuAnime -> Icons.Outlined.AutoAwesome
            HomeSection.WibuLatestAnime -> Icons.Outlined.NewReleases
            HomeSection.WibuLatestMovie -> Icons.Outlined.PlayCircleOutline
            HomeSection.WibuLatestDonghua -> Icons.Outlined.AutoAwesome
            HomeSection.WibuCompletedAnime -> Icons.Outlined.HourglassTop
            HomeSection.WibuCompletedMovie -> Icons.Outlined.PlayCircleOutline
            HomeSection.WibuCompletedDonghua -> Icons.Outlined.HourglassTop
        }
