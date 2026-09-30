package com.uwu.animex.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uwu.animex.data.Onboarding

@Composable
fun App() {
    val nav = rememberNavController()
    val startDestination = remember { if (Onboarding.done.value) "main" else "onboarding" }

    val pendingDetail by NotificationRouter.pendingDetail.collectAsState()
    LaunchedEffect(pendingDetail) {
        val id = pendingDetail ?: return@LaunchedEffect
        NotificationRouter.pendingDetail.value = null
        nav.navigate("detail/$id")
    }

    fun openFilter(kind: String, id: String, title: String) {
        nav.navigate("filter/$kind/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }

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
        composable("main") {
            MainScreen(
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
            ProfileScreen()
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
}
