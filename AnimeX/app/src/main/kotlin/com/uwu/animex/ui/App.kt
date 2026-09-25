package com.uwu.animex.ui

import android.net.Uri
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

    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                onOpen = { nav.navigate("detail/$it") },
                onMore = { nav.navigate("list/$it") },
                onFilter = { kind, id, title -> openFilter(kind, id, title) },
                onOpenCategory = { nav.navigate("category") },
                onOpenYear = { nav.navigate("year") },
                onOpenCuplix = { startId ->
                    nav.navigate("cuplix?id=${Uri.encode(startId.orEmpty())}")
                },
            )
        }
        composable("category") {
            CategoryScreen(
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
        composable(
            "cuplix?id={id}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { e ->
            val startId = e.arguments?.getString("id")?.takeIf { it.isNotBlank() }
            CuplixScreen(
                initialId = startId,
                onBack = { nav.popBackStack() },
                onOpenAnime = { nav.navigate("detail/$it") },
            )
        }
        composable("detail/{id}") { e ->
            DetailScreen(
                id = e.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onPlay = { epId, title -> nav.navigate("player/$epId?title=${Uri.encode(title)}") },
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
