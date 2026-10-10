package com.uwu.animex.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uwu.animex.data.local.Onboarding
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.LocalOpenStatusSheet
import com.uwu.animex.ui.common.OfflineBanner
import com.uwu.animex.ui.detail.DetailScreen
import com.uwu.animex.ui.detail.MalEditSheet
import com.uwu.animex.ui.download.DownloadsPage
import com.uwu.animex.ui.list.FilterListScreen
import com.uwu.animex.ui.list.ListScreen
import com.uwu.animex.ui.main.MainScreen
import com.uwu.animex.ui.onboarding.OnboardingScreen
import com.uwu.animex.ui.player.CuplixPlayerScreen
import com.uwu.animex.ui.player.PlayerScreen
import com.uwu.animex.ui.profile.AboutScreen
import com.uwu.animex.ui.profile.ProfileScreen
import com.uwu.animex.ui.search.CategoryScreen
import com.uwu.animex.ui.search.StudioScreen
import com.uwu.animex.ui.search.TypeScreen
import com.uwu.animex.ui.search.YearScreen
import com.uwu.animex.ui.settings.SettingsSheet
import com.uwu.animex.ui.update.UpdateCheckerHost
import com.uwu.animex.ui.update.UpdateScreen

@Composable
fun App() {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Satu-satunya host sheet status tontonan: dibuka dari long-click card poster di layar mana pun
    var statusTarget by remember { mutableStateOf<Movie?>(null) }
    val openStatusSheet = remember { { m: Movie -> statusTarget = m } }
    val startDestination = remember { if (Onboarding.done.value) "main" else "onboarding" }

    val pendingDetail by NotificationRouter.pendingDetail.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDetail) {
        val id = pendingDetail ?: return@LaunchedEffect
        NotificationRouter.pendingDetail.value = null
        showSettings = false
        nav.navigate("detail/$id")
    }

    val pendingUpdate by NotificationRouter.pendingOpenUpdate.collectAsStateWithLifecycle()
    LaunchedEffect(pendingUpdate) {
        if (!pendingUpdate) return@LaunchedEffect
        NotificationRouter.pendingOpenUpdate.value = false
        showSettings = false
        nav.navigate("update")
    }

    UpdateCheckerHost()

    fun openFilter(
        kind: String,
        id: String,
        title: String,
    ) {
        nav.navigate("filter/$kind/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }

    CompositionLocalProvider(LocalOpenStatusSheet provides openStatusSheet) {
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
                                    "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}",
                            )
                        },
                        onPlayCuplix = { c -> nav.navigateCuplix(c, source = "home") },
                        onOpenProfile = { showSettings = true },
                    )
                }
                composable("mal") {
                    ProfileScreen(onBack = { nav.popBackStack() })
                }
                composable("about") {
                    AboutScreen(
                        onBack = { nav.popBackStack() },
                        onOpenUpdate = { nav.navigate("update") },
                    )
                }
                composable("downloads") {
                    DownloadsPage(
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate("detail/$it") },
                        onPlay = { epId, title, movieId, epIndex ->
                            nav.navigate(
                                "player/$epId?title=${Uri.encode(title)}" +
                                    "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}",
                            )
                        },
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
                                    "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}",
                            )
                        },
                    )
                }
                composable(
                    "filter/{kind}/{id}?title={title}",
                    arguments =
                        listOf(
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
                                    "&movieId=${Uri.encode(movieId.orEmpty())}&epIndex=${Uri.encode(epIndex.orEmpty())}",
                            )
                        },
                        onPlayCuplix = { c -> nav.navigateCuplix(c, source = "movie") },
                    )
                }
                composable(
                    "cuplix?startId={startId}&movieId={movieId}&source={source}&episodeId={episodeId}&timeStart={timeStart}&timeEnd={timeEnd}&caption={caption}&image={image}&title={title}",
                    arguments =
                        listOf(
                            navArgument("startId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("movieId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("source") {
                                type = NavType.StringType
                                defaultValue = "home"
                            },
                            navArgument("episodeId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("timeStart") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("timeEnd") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("caption") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("image") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("title") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                ) { e ->
                    CuplixPlayerScreen(
                        startId = e.arguments?.getString("startId")?.takeIf { it.isNotBlank() },
                        movieId = e.arguments?.getString("movieId")?.takeIf { it.isNotBlank() },
                        source = e.arguments?.getString("source")?.takeIf { it.isNotBlank() } ?: "home",
                        seed =
                            Cuplix(
                                id = e.arguments?.getString("startId")?.takeIf { it.isNotBlank() },
                                episode_id = e.arguments?.getString("episodeId")?.takeIf { it.isNotBlank() },
                                movie_id = e.arguments?.getString("movieId")?.takeIf { it.isNotBlank() },
                                time_start = e.arguments?.getString("timeStart")?.takeIf { it.isNotBlank() },
                                time_end = e.arguments?.getString("timeEnd")?.takeIf { it.isNotBlank() },
                                caption = e.arguments?.getString("caption")?.takeIf { it.isNotBlank() },
                                image = e.arguments?.getString("image")?.takeIf { it.isNotBlank() },
                                title = e.arguments?.getString("title")?.takeIf { it.isNotBlank() },
                            ),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    "player/{epId}?title={title}&movieId={movieId}&epIndex={epIndex}&cuplixId={cuplixId}&startMs={startMs}&endMs={endMs}&caption={caption}",
                    arguments =
                        listOf(
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
                            navArgument("cuplixId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("startMs") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("endMs") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("caption") {
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
                        cuplixId = e.arguments?.getString("cuplixId")?.takeIf { it.isNotBlank() },
                        startMs = e.arguments?.getString("startMs")?.toLongOrNull(),
                        endMs = e.arguments?.getString("endMs")?.toLongOrNull(),
                        caption = e.arguments?.getString("caption")?.takeIf { it.isNotBlank() },
                    )
                }
            }
            SettingsSheet(
                visible = showSettings && backEntry?.destination?.route == "main",
                onDismiss = { showSettings = false },
                onOpenMal = { nav.navigate("mal") },
                onOpenAbout = { nav.navigate("about") },
                onOpenDownloads = { nav.navigate("downloads") },
            )
            OfflineBanner(Modifier.align(Alignment.TopCenter))

            statusTarget?.let { target ->
                key(target.id) {
                    MalEditSheet(
                        movie = target,
                        onDismiss = { statusTarget = null },
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateCuplix(
    c: Cuplix,
    source: String = "home",
) {
    val startId = Uri.encode(c.id?.takeIf { it.isNotBlank() } ?: c.episode_id.orEmpty())
    val movieId = Uri.encode(c.movieId.orEmpty())
    val episodeId = Uri.encode(c.episode_id.orEmpty())
    val timeStart = Uri.encode(c.time_start.orEmpty())
    val timeEnd = Uri.encode(c.time_end.orEmpty())
    val caption = Uri.encode(c.text)
    val image = Uri.encode(c.bubbleImage.orEmpty())
    val title = Uri.encode(c.title.orEmpty())
    navigate(
        "cuplix?startId=$startId&movieId=$movieId&source=${Uri.encode(source)}" +
            "&episodeId=$episodeId&timeStart=$timeStart&timeEnd=$timeEnd" +
            "&caption=$caption&image=$image&title=$title",
    )
}
