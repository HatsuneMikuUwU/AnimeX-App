package com.uwu.animex.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uwu.animex.data.local.Onboarding
import com.uwu.animex.ui.common.OfflineBanner
import com.uwu.animex.ui.detail.DetailScreen
import com.uwu.animex.ui.list.FilterListScreen
import com.uwu.animex.ui.list.ListScreen
import com.uwu.animex.ui.main.MainScreen
import com.uwu.animex.ui.onboarding.OnboardingScreen
import com.uwu.animex.ui.player.PlayerScreen
import com.uwu.animex.ui.profile.AboutScreen
import com.uwu.animex.ui.profile.ProfileScreen
import com.uwu.animex.ui.search.CategoryScreen
import com.uwu.animex.ui.search.StudioScreen
import com.uwu.animex.ui.search.TypeScreen
import com.uwu.animex.ui.search.YearScreen
import com.uwu.animex.ui.update.UpdateCheckerHost
import com.uwu.animex.ui.update.UpdateScreen

@Composable
fun App() {
    val nav = rememberNavController()
    val startDestination = remember { if (Onboarding.done.value) "main" else "onboarding" }

    val pendingDetail by NotificationRouter.pendingDetail.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDetail) {
        val id = pendingDetail ?: return@LaunchedEffect
        NotificationRouter.pendingDetail.value = null
        nav.navigate("detail/$id")
    }

    val pendingUpdate by NotificationRouter.pendingOpenUpdate.collectAsStateWithLifecycle()
    LaunchedEffect(pendingUpdate) {
        if (!pendingUpdate) return@LaunchedEffect
        NotificationRouter.pendingOpenUpdate.value = false
        nav.navigate("update")
    }

    UpdateCheckerHost()

    fun openFilter(kind: String, id: String, title: String) {
        nav.navigate("filter/$kind/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(280)) },
            exitTransition = { fadeOut(tween(280)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(280)) },
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onFinish = {
                        Onboarding.complete()
                        nav.navigate("main") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                )
            }
            composable("update") {
                UpdateScreen(onBack = { nav.popBackStack() })
            }
            composable("main") {
                MainScreen(
                    onOpenUpdate = { nav.navigate("update") },
                    onOpen = { nav.navigate("detail/$it") },
                    onMore = { nav.navigate("list/$it") },
                    onFilter = { kind, id, title -> openFilter(kind, id, title) },
                    onOpenCategory = { nav.navigate("category") },
                    onOpenStudio = { nav.navigate("studio") },
                    onOpenYear = { nav.navigate("year") },
                    onOpenType = { nav.navigate("type") },
                    onPlay = { epId, title, movieId, epIndex ->
                        nav.navigate(
                            "player/$epId?title=${Uri.encode(title)}" +
                                "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}"
                        )
                    },
                    onOpenProfile = { nav.navigate("profile") },
                )
            }
            composable("profile") {
                ProfileScreen(
                    onBack = { nav.popBackStack() },
                    onOpenAbout = { nav.navigate("about") },
                )
            }
            composable("about") {
                AboutScreen(
                    onBack = { nav.popBackStack() },
                    onOpenUpdate = { nav.navigate("update") },
                )
            }
            composable("category") {
                CategoryScreen(
                    onBack = { nav.popBackStack() },
                    onFilter = { kind, id, title -> openFilter(kind, id, title) },
                )
            }
            composable("studio") {
                StudioScreen(
                    onBack = { nav.popBackStack() },
                    onFilter = { kind, id, title -> openFilter(kind, id, title) },
                )
            }
            composable("year") {
                YearScreen(
                    onBack = { nav.popBackStack() },
                    onFilter = { kind, id, title -> openFilter(kind, id, title) },
                )
            }
            composable("type") {
                TypeScreen(
                    onBack = { nav.popBackStack() },
                    onFilter = { kind, id, title -> openFilter(kind, id, title) },
                )
            }
            composable("list/{key}") { e ->
                ListScreen(
                    key = e.arguments?.getString("key").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate("detail/$it") },
                    onPlay = { epId, title, movieId, epIndex ->
                        nav.navigate(
                            "player/$epId?title=${Uri.encode(title)}" +
                                "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}"
                        )
                    },
                )
            }
            composable(
                "filter/{kind}/{id}?title={title}",
                arguments = listOf(
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { e ->
                val kind = e.arguments?.getString("kind").orEmpty()
                val id = Uri.decode(e.arguments?.getString("id").orEmpty())
                val title = Uri.decode(e.arguments?.getString("title").orEmpty()).ifBlank { id }
                FilterListScreen(
                    kind = kind,
                    id = id,
                    title = title,
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate("detail/$it") },
                )
            }
            composable("detail/{id}") { e ->
                DetailScreen(
                    id = e.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpen = { seasonId -> nav.navigate("detail/$seasonId") },
                    onPlay = { epId, title, movieId, epIndex ->
                        nav.navigate(
                            "player/$epId?title=${Uri.encode(title)}" +
                                "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}"
                        )
                    },
                )
            }
            composable(
                "player/{epId}?title={title}&movieId={movieId}&epIndex={epIndex}",
                arguments = listOf(
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("movieId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("epIndex") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { e ->
                PlayerScreen(
                    epId = e.arguments?.getString("epId").orEmpty(),
                    title = e.arguments?.getString("title").orEmpty(),
                    movieId = e.arguments?.getString("movieId")?.takeIf { it.isNotBlank() },
                    epIndex = e.arguments?.getString("epIndex")?.takeIf { it.isNotBlank() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        OfflineBanner(Modifier.align(Alignment.TopCenter))
    }
}
