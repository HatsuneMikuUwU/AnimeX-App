package com.uwu.animex.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun App() {
    val nav = rememberNavController()

    fun openFilter(kind: String, id: String, title: String) {
        nav.navigate("filter/$kind/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }

    NavHost(
        nav,
        startDestination = "main",
        enterTransition = { fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(280)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { fadeOut(tween(280)) },
    ) {
        composable("main") {
            MainScreen(
                onOpen = { nav.navigate("detail/$it") },
                onMore = { nav.navigate("list/$it") },
                onFilter = { kind, id, title -> openFilter(kind, id, title) },
                onOpenCategory = { nav.navigate("category") },
                onOpenStudio = { nav.navigate("studio") },
                onOpenYear = { nav.navigate("year") },
                onOpenType = { nav.navigate("type") },
                onPlay = { epId, title -> nav.navigate("player/$epId?title=${Uri.encode(title)}") },
                onOpenProfile = { nav.navigate("profile") },
            )
        }
        composable("profile") {
            ProfileScreen(onBack = { nav.popBackStack() })
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
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
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
                onPlay = { epId, title -> nav.navigate("player/$epId?title=${Uri.encode(title)}") },
                onOpenCharacter = { cid, name ->
                    nav.navigate("character/$cid?name=${Uri.encode(name)}")
                },
            )
        }
        composable(
            "character/{id}?name={name}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { e ->
            val cid = e.arguments?.getString("id")?.toIntOrNull() ?: 0
            val name = Uri.decode(e.arguments?.getString("name").orEmpty())
            CharacterScreen(
                id = cid,
                fallbackName = name,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "player/{epId}?title={title}",
            arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "" }),
        ) { e ->
            PlayerScreen(
                epId = e.arguments?.getString("epId").orEmpty(),
                title = e.arguments?.getString("title").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
