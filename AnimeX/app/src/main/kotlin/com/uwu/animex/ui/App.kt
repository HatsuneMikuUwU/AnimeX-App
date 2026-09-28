package com.uwu.animex.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedContent(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedTransitionScope,
        LocalNavAnimatedVisibilityScope provides animatedVisibilityScope,
        content = content,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun App() {
    val nav = rememberNavController()

    fun openFilter(kind: String, id: String, title: String) {
        nav.navigate("filter/$kind/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }

    SharedTransitionLayout {
        val sharedScope = this

        NavHost(
            nav,
            startDestination = "main",
            enterTransition = { fadeIn(tween(280)) },
            exitTransition = { fadeOut(tween(280)) },
            popEnterTransition = { fadeIn(tween(280)) },
            popExitTransition = { fadeOut(tween(280)) },
        ) {
            composable("main") {
                SharedContent(sharedScope, this) {
                    MainScreen(
                        onOpen = { nav.navigate("detail/$it") },
                        onMore = { nav.navigate("list/$it") },
                        onFilter = { kind, id, title -> openFilter(kind, id, title) },
                        onOpenCategory = { nav.navigate("category") },
                        onOpenStudio = { nav.navigate("studio") },
                        onOpenYear = { nav.navigate("year") },
                        onOpenType = { nav.navigate("type") },
                        onPlay = { epId, title -> nav.navigate("player/$epId?title=${Uri.encode(title)}") },
                    )
                }
            }
            composable("category") {
                SharedContent(sharedScope, this) {
                    CategoryScreen(
                        onBack = { nav.popBackStack() },
                        onFilter = { kind, id, title -> openFilter(kind, id, title) },
                    )
                }
            }
            composable("studio") {
                SharedContent(sharedScope, this) {
                    StudioScreen(
                        onBack = { nav.popBackStack() },
                        onFilter = { kind, id, title -> openFilter(kind, id, title) },
                    )
                }
            }
            composable("year") {
                SharedContent(sharedScope, this) {
                    YearScreen(
                        onBack = { nav.popBackStack() },
                        onFilter = { kind, id, title -> openFilter(kind, id, title) },
                    )
                }
            }
            composable("type") {
                SharedContent(sharedScope, this) {
                    TypeScreen(
                        onBack = { nav.popBackStack() },
                        onFilter = { kind, id, title -> openFilter(kind, id, title) },
                    )
                }
            }
            composable("list/{key}") { e ->
                SharedContent(sharedScope, this) {
                    ListScreen(
                        key = e.arguments?.getString("key").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate("detail/$it") },
                    )
                }
            }
            composable(
                "filter/{kind}/{id}?title={title}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                SharedContent(sharedScope, this) {
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
            }
            composable("detail/{id}") { e ->
                SharedContent(sharedScope, this) {
                    DetailScreen(
                        id = e.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onPlay = { epId, title -> nav.navigate("player/$epId?title=${Uri.encode(title)}") },
                    )
                }
            }
            composable(
                "player/{epId}?title={title}",
                arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "" }),
            ) { e ->
                SharedContent(sharedScope, this) {
                    PlayerScreen(
                        epId = e.arguments?.getString("epId").orEmpty(),
                        title = e.arguments?.getString("title").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
