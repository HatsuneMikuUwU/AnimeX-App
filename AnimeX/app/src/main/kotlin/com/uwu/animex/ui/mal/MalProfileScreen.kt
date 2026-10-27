@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.mal

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.mal.hasStatsData
import com.uwu.animex.data.mal.toListStats
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.profile.LoginPrompt
import com.uwu.animex.ui.profile.ProfileActions
import com.uwu.animex.ui.profile.ProfileContent
import com.uwu.animex.ui.profile.ProfileHistorySection
import com.uwu.animex.ui.profile.ProfileInfo
import com.uwu.animex.ui.profile.ProfileScaffold
import com.uwu.animex.ui.profile.ProfileTabState
import com.uwu.animex.ui.profile.prettyDate
import kotlinx.coroutines.launch

@Composable
fun MalProfileScreen(
    onBack: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }

    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val message by Mal.message.collectAsStateWithLifecycle()

    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { Mal.refreshUser() }
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { MalLibrary.refresh(force = MalLibrary.lacksStatsData()) }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            Mal.clearMessage()
        }
    }

    if (confirmLogout) {
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
            onDismiss = { confirmLogout = false },
            title = "Logout dari MAL?",
            text = {
                Text(
                    "Sinkronisasi ke MyAnimeList bakal berhenti sampai kamu login lagi. " +
                        "Status tontonan yang kesimpen di HP ini juga bakal dihapus.",
                )
            },
            confirmButton = {
                DialogDestructiveButton("Logout") {
                    Mal.logout()
                    confirmLogout = false
                }
            },
            dismissButton = { DialogCancelButton { confirmLogout = false } },
        )
    }

    val busy by Mal.busy.collectAsStateWithLifecycle()
    val userState by Mal.user.collectAsStateWithLifecycle()
    val libraryItems by MalLibrary.items.collectAsStateWithLifecycle()
    val libraryRefreshing by MalLibrary.refreshing.collectAsStateWithLifecycle()
    val libraryError by MalLibrary.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val listStats = remember(libraryItems) { libraryItems.toListStats() }
    val libraryReady =
        libraryItems.hasStatsData() ||
            (libraryItems.isEmpty() && MalLibrary.loaded && libraryError == null)
    val libraryFailed = !libraryReady && !libraryRefreshing && libraryError != null
    val libraryLoading = !libraryReady && !libraryFailed
    ProfileScaffold(
        title = "MAL",
        onBack = onBack,
        snackbar = snackbar,
        actions =
            if (loggedIn) {
                (
                    {
                        ProfileActions(
                            openLabel = "Intip profil di MAL",
                            onOpen = { userState?.name?.let { uri.openUri(Mal.PROFILE_URL + it) } },
                            onLogout = { confirmLogout = true },
                        )
                    }
                )
            } else {
                null
            },
        tabs = if (loggedIn) ProfileTabState(tab) { tab = it } else null,
        refreshing = pullRefreshing,
        onRefresh =
            if (loggedIn) {
                {
                    scope.launch {
                        pullRefreshing = true
                        try {
                            runCatching { Mal.refreshUser() }
                            runCatching { MalLibrary.refresh(force = true) }
                            runCatching { Mal.refreshHistory() }
                        } finally {
                            pullRefreshing = false
                        }
                    }
                }
            } else {
                null
            },
    ) {
        if (!loggedIn) {
            LoginPrompt(
                title = "Sambungin MyAnimeList",
                description = "Progres nonton dan status anime bakal otomatis nyambung ke daftar MAL kamu.",
                buttonText = "Login pakai MAL",
                busy = busy,
                onLogin = { Mal.startLogin(ctx) },
            )
        } else {
            val user = userState
            val historyState by Mal.history.collectAsStateWithLifecycle()
            ProfileContent(
                info =
                    ProfileInfo(
                        name = user?.name,
                        picture = user?.picture,
                        location = user?.location,
                        birthday = user?.birthday?.let { prettyDate(it, "yyyy-MM-dd", "MMM d, yyyy") },
                        joined = user?.joined_at?.let { prettyDate(it, "yyyy-MM-dd'T'HH:mm:ssXXX", "MMM d, yyyy") },
                    ),
                tab = tab,
                historyContent = {
                    ProfileHistorySection(
                        state = historyState,
                        onLoad = { scope.launch { Mal.refreshHistory() } },
                        onOpenDetail = onOpenDetail,
                        movieIdFor = { Mal.movieIdFor(it) },
                        link = { movieId, malId -> Mal.link(movieId, malId) },
                        snackbar = snackbar,
                        failedMessage = "Riwayat MAL gagal dimuat",
                    )
                },
            ) {
                MalStatsSection(
                    malStats = user?.anime_statistics,
                    s = listStats,
                    libraryLoading = libraryLoading,
                    libraryFailed = libraryFailed,
                    onRetry = {
                        scope.launch {
                            runCatching { Mal.refreshUser() }
                            runCatching { MalLibrary.refresh(force = true) }
                        }
                    },
                )
            }
        }
    }
}
