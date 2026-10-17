package com.uwu.animex.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.ui.common.AnimatedNavIcon
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.FloatingTabBarOverlay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AniListProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val loggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val user by AniList.user.collectAsStateWithLifecycle()
    val busy by AniList.busy.collectAsStateWithLifecycle()
    val statsLoading by AniList.statsLoading.collectAsStateWithLifecycle()
    val message by AniList.message.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var activity by remember { mutableStateOf<ActivityState>(ActivityState.Loading) }
    var activityNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) runCatching { AniList.refreshUser() }
    }
    LaunchedEffect(loggedIn, activityNonce) {
        if (!loggedIn) return@LaunchedEffect
        activity = ActivityState.Loading
        activity =
            runCatching { AniList.activities().map { it.toEntry() } }.fold(
                onSuccess = { ActivityState.Ready(it) },
                onFailure = { ActivityState.Error(it.message ?: "AniList lagi bermasalah") },
            )
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            AniList.clearMessage()
        }
    }

    if (confirmLogout) {
        AppDialog(
            icon = Icons.AutoMirrored.Outlined.Logout,
            onDismiss = { confirmLogout = false },
            title = "Logout dari AniList?",
            text = {
                Text("Sinkronisasi ke AniList bakal berhenti sampai kamu login lagi. Akun MAL tetap tersambung.")
            },
            confirmButton = {
                DialogDestructiveButton("Logout") {
                    AniList.logout()
                    confirmLogout = false
                }
            },
            dismissButton = { DialogCancelButton { confirmLogout = false } },
        )
    }

    ProfileScaffold(
        title = "AniList",
        onBack = onBack,
        snackbar = snackbar,
        overlay =
            if (loggedIn) {
                { backdrop ->
                    FloatingTabBarOverlay(
                        items = profileTabs,
                        selectedIndex = { tab },
                        onSelected = { tab = it },
                        backdrop = backdrop,
                        label = { it.label },
                        icon = { item, i ->
                            AnimatedNavIcon(tab == i, item.icon, item.label, selectedIcon = item.selectedIcon)
                        },
                    )
                }
            } else {
                null
            },
    ) {
        if (!loggedIn) {
            LoginPrompt(
                title = "Sambungin AniList",
                description =
                    "Progres nonton, status, dan rating bakal otomatis nyambung ke daftar AniList kamu. Kalau " +
                        "akun MAL juga tersambung, satu perubahan dikirim ke keduanya.",
                buttonText = "Login pakai AniList",
                busy = busy,
                onLogin = { AniList.startLogin(context) },
            )
        } else {
            val u = user
            ProfileContent(
                info =
                    ProfileInfo(
                        name = u?.name,
                        picture = u?.avatar,
                        joined = u?.createdAt?.let { formatEpochSeconds(it, "MMM d, yyyy") },
                    ),
                openLabel = "Intip profil di AniList",
                onOpen = { u?.name?.let { uriHandler.openUri(AniList.PROFILE_URL + it) } },
                onLogout = { confirmLogout = true },
                tab = tab,
                activityContent = {
                    ActivitySection(
                        state = activity,
                        subtitle = "Aktivitas anime terakhir di AniList kamu.",
                        onRetry = { activityNonce++ },
                    )
                },
            ) {
                AniListStatsSection(
                    stats = u?.stats,
                    loading = statsLoading,
                    onRetry = { scope.launch { runCatching { AniList.refreshUser() } } },
                )
            }
        }
    }
}

private fun formatEpochSeconds(
    seconds: Long,
    pattern: String,
): String? =
    runCatching {
        java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(java.util.Date(seconds * 1000))
    }.getOrNull()?.takeIf { it.isNotBlank() }
