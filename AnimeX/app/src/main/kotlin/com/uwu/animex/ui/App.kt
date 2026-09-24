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
    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                onOpen = { nav.navigate("detail/$it") },
                onMore = { nav.navigate("list/$it") },
            )
        }
        composable("list/{key}") { e ->
            ListScreen(
                key = e.arguments?.getString("key").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate("detail/$it") },
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
