package com.uwu.animex.ui.navigation

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uwu.animex.data.api.CuplixFeed
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.Onboarding
import com.uwu.animex.data.model.Movie
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.anilist.AniListProfileScreen
import com.uwu.animex.ui.auth.LoginScreen
import com.uwu.animex.ui.background.AnimatedBackground
import com.uwu.animex.ui.background.BackgroundType
import com.uwu.animex.ui.background.LocalAnimatedBackgroundActive
import com.uwu.animex.ui.background.rememberActiveBackground
import com.uwu.animex.ui.cuplix.CuplixAllScreen
import com.uwu.animex.ui.cuplix.CuplixShortsScreen
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.LocalOpenStatusSheet
import com.uwu.animex.ui.common.OfflineBanner
import com.uwu.animex.ui.detail.DetailScreen
import com.uwu.animex.ui.discussion.DiscussionScreen
import com.uwu.animex.ui.download.DownloadsPage
import com.uwu.animex.ui.list.FilterListScreen
import com.uwu.animex.ui.list.ListScreen
import com.uwu.animex.ui.main.MainScreen
import com.uwu.animex.ui.mal.MalProfileScreen
import com.uwu.animex.ui.notifications.NotificationCenterScreen
import com.uwu.animex.ui.onboarding.OnboardingScreen
import com.uwu.animex.ui.player.PipHelper
import com.uwu.animex.ui.player.PlayerScreen
import com.uwu.animex.ui.profile.AboutScreen
import com.uwu.animex.ui.search.CategoryScreen
import com.uwu.animex.ui.search.StudioScreen
import com.uwu.animex.ui.search.TypeScreen
import com.uwu.animex.ui.search.YearScreen
import com.uwu.animex.ui.settings.SettingsSheet
import com.uwu.animex.ui.tracker.TrackerEditSheet
import com.uwu.animex.ui.update.UpdateCheckerHost
import com.uwu.animex.ui.update.UpdateScreen

@Composable
fun App() {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()

    LaunchedEffect(nav) {
        nav.currentBackStack.collect { stack ->
            val inPlayer =
                stack.any {
                    val route = it.destination.route
                    route?.startsWith("player") == true || route?.startsWith("cuplix") == true
                }
            if (!inPlayer && PlaybackSessions.current != null) PlaybackSessions.stop()
        }
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

    val pendingCenter by NotificationRouter.pendingOpenCenter.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCenter) {
        if (!pendingCenter) return@LaunchedEffect
        NotificationRouter.pendingOpenCenter.value = false
        showSettings = false
        nav.navigate("notifications")
    }

    val pendingSetup by NotificationRouter.pendingOpenSetup.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSetup) {
        if (!pendingSetup) return@LaunchedEffect
        NotificationRouter.pendingOpenSetup.value = false
        nav.navigate("notification-setup")
    }

    UpdateCheckerHost()

    fun openFilter(
        kind: String,
        id: String,
        title: String,
    ) {
        nav.navigate("filter/$kind/${Uri.encode(id)}?title=${Uri.encode(title)}")
    }

    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val route = backEntry?.destination?.route ?: startDestination

    val playerInPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val playerFullscreen =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE || playerInPip
    val backgroundActive =
        appearance.backgroundType != BackgroundType.NONE &&
            !route.startsWith("onboarding") &&
            !(route.startsWith("player") && playerFullscreen)

    CompositionLocalProvider(
        LocalOpenStatusSheet provides openStatusSheet,
        LocalAnimatedBackgroundActive provides backgroundActive,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AnimatedVisibility(
                visible = backgroundActive,
                enter = AppMotion.fadeEnter(),
                exit = AppMotion.fadeExit(),
            ) {
                AnimatedBackground(
                    type = rememberActiveBackground(),
                    enableParallax = appearance.backgroundParallax,
                )
            }
            val density = LocalDensity.current
            val slidePx = remember(density) { with(density) { AppMotion.SHARED_AXIS_SLIDE_DP.dp.roundToPx() } }
            NavHost(
                nav,
                startDestination = startDestination,
                enterTransition = { AppMotion.sharedAxisXEnter(forward = true, slidePx = slidePx) },
                exitTransition = { AppMotion.sharedAxisXExit(forward = true, slidePx = slidePx) },
                popEnterTransition = { AppMotion.sharedAxisXEnter(forward = false, slidePx = slidePx) },
                popExitTransition = { AppMotion.sharedAxisXExit(forward = false, slidePx = slidePx) },
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
                composable("notifications") {
                    NotificationCenterScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate("detail/$it") },
                    )
                }
                composable("notification-setup") {
                    OnboardingScreen(onFinish = { nav.popBackStack() }, permissionsOnly = true)
                }
                composable("update") {
                    UpdateScreen(onBack = { nav.popBackStack() })
                }
                composable("main") {
                    MainScreen(
                        onOpenUpdate = { nav.navigate("update") },
                        onOpen = { nav.navigate("detail/$it") },
                        onMore = { nav.navigate(if (it == "cuplix") "clips" else "list/$it") },
                        onFilter = { kind, id, title -> openFilter(kind, id, title) },
                        onOpenCategory = { nav.navigate("category") },
                        onOpenStudio = { nav.navigate("studio") },
                        onOpenYear = { nav.navigate("year") },
                        onOpenType = { nav.navigate("type") },
                        onPlay = { epId, title, movieId, epIndex ->
                            nav.navigate(
                                "player/$epId?title=${Uri.encode(title)}" +
                                    "&movieId=${Uri.encode(movieId.orEmpty())}" +
                                    "&epIndex=${Uri.encode(epIndex.orEmpty())}",
                            )
                        },
                        onPlayCuplix = { clip ->
                            CuplixFeed.start(clip)
                            nav.navigate("cuplix/${Uri.encode(cuplixKey(clip))}")
                        },
                        onOpenProfile = { showSettings = true },
                        onOpenNotifications = { nav.navigate("notifications") },
                    )
                }
                composable("mal") {
                    MalProfileScreen(
                        onBack = { nav.popBackStack() },
                        onOpenDetail = { nav.navigate("detail/$it") },
                    )
                }
                composable("anilist") {
                    AniListProfileScreen(
                        onBack = { nav.popBackStack() },
                        onOpenDetail = { nav.navigate("detail/$it") },
                    )
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
                                    "&movieId=${Uri.encode(movieId.orEmpty())}" +
                                    "&epIndex=${Uri.encode(epIndex.orEmpty())}",
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
                                    "&movieId=${Uri.encode(movieId.orEmpty())}" +
                                    "&epIndex=${Uri.encode(epIndex.orEmpty())}",
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
                        onOpenDiscussion = { movieId, title ->
                            nav.navigate(
                                "discussion/$movieId?title=${Uri.encode(title.orEmpty())}",
                            )
                        },
                        onOpenEpisodeComment = { episodeId, title ->
                            nav.navigate(
                                "episode-comment/$episodeId?title=${Uri.encode(title.orEmpty())}",
                            )
                        },
                        onPlay = { epId, title, movieId, epIndex ->
                            nav.navigate(
                                "player/$epId?title=${Uri.encode(title)}" +
                                    "&movieId=${Uri.encode(movieId.orEmpty())}" +
                                    "&epIndex=${Uri.encode(epIndex.orEmpty())}",
                            )
                        },
                    )
                }
                composable(
                    "discussion/{id}?title={title}",
                    arguments =
                        listOf(
                            navArgument("title") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                ) { e ->
                    DiscussionScreen(
                        movieId = e.arguments?.getString("id").orEmpty(),
                        movieTitle = Uri.decode(e.arguments?.getString("title").orEmpty()).ifBlank { null },
                        onBack = { nav.popBackStack() },
                        onNeedLogin = { nav.navigate("login") },
                    )
                }
                composable(
                    "episode-comment/{id}?title={title}",
                    arguments =
                        listOf(
                            navArgument("title") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                ) { e ->
                    DiscussionScreen(
                        movieId = e.arguments?.getString("id").orEmpty(),
                        movieTitle = Uri.decode(e.arguments?.getString("title").orEmpty()).ifBlank { null },
                        scope = com.uwu.animex.ui.viewmodel.DiscussionScope.Episode,
                        onBack = { nav.popBackStack() },
                        onNeedLogin = { nav.navigate("login") },
                    )
                }
                composable("login") {
                    LoginScreen(
                        onBack = { nav.popBackStack() },
                        onLoggedIn = { nav.popBackStack() },
                    )
                }
                composable("clips") {
                    CuplixAllScreen(
                        onBack = { nav.popBackStack() },
                        onPlay = { clip ->
                            CuplixFeed.start(clip)
                            nav.navigate("cuplix/${Uri.encode(cuplixKey(clip))}")
                        },
                    )
                }
                composable("cuplix/{clipId}") { e ->
                    CuplixShortsScreen(
                        startClipId = Uri.decode(e.arguments?.getString("clipId").orEmpty()),
                        onBack = { nav.popBackStack() },
                        onOpenAnime = { animeId -> nav.navigate("detail/$animeId") },
                        onNeedLogin = { nav.navigate("login") },
                    )
                }
                composable(
                    "player/{epId}?title={title}&movieId={movieId}&epIndex={epIndex}&startAtMs={startAtMs}",
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
                            navArgument("startAtMs") {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                ) { e ->
                    PlayerScreen(
                        epId = e.arguments?.getString("epId").orEmpty(),
                        title = e.arguments?.getString("title").orEmpty(),
                        movieId = e.arguments?.getString("movieId")?.takeIf { it.isNotBlank() },
                        epIndex = e.arguments?.getString("epIndex")?.takeIf { it.isNotBlank() },
                        startAtMs = e.arguments?.getLong("startAtMs")?.takeIf { it >= 0L },
                        onBack = { nav.popBackStack() },
                        onOpenAnime = { animeId ->
                            nav.navigate("detail/$animeId") {
                                popUpTo("player/{epId}?title={title}&movieId={movieId}&epIndex={epIndex}&startAtMs={startAtMs}") {
                                    inclusive = true
                                }
                            }
                        },
                        onNeedLogin = { nav.navigate("login") },
                    )
                }
            }
            SettingsSheet(
                visible = showSettings && backEntry?.destination?.route == "main",
                onDismiss = { showSettings = false },
                onOpenMal = { nav.navigate("mal") },
                onOpenAniList = { nav.navigate("anilist") },
                onOpenAbout = { nav.navigate("about") },
                onOpenDownloads = { nav.navigate("downloads") },
            )
            OfflineBanner(Modifier.align(Alignment.TopCenter))

            statusTarget?.let { target ->
                key(target.id) {
                    TrackerEditSheet(
                        movie = target,
                        onDismiss = { statusTarget = null },
                    )
                }
            }
        }
    }
}
