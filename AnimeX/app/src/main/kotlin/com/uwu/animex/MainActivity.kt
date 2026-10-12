package com.uwu.animex

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Onboarding
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.update.AppUpdate
import com.uwu.animex.ui.navigation.App
import com.uwu.animex.ui.navigation.NotificationRouter
import com.uwu.animex.ui.theme.AppTheme
import com.uwu.animex.ui.theme.UI_SCALE_DEFAULT
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.theme.withUiScale
import com.uwu.animex.work.EpisodeCheckWorker

class MainActivity : ComponentActivity() {
    /** UI scale already baked into this activity's context; live changes are applied on top of it as a ratio. */
    private var baseUiScale = UI_SCALE_DEFAULT

    override fun attachBaseContext(newBase: Context) {
        baseUiScale = Appearance.readUiScale(newBase)
        super.attachBaseContext(newBase.withUiScale(baseUiScale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Appearance.init(this)
        SyncSourcePref.init(this)
        Onboarding.init(this)
        PlayerPrefs.init(this)
        History.init(this)
        Progress.init(this)
        Bookmarks.init(this)
        SearchHistory.init(this)
        Downloads.init(this)
        Mal.init(this)
        AniList.init(this)
        EpisodeAlerts.init(this)
        AppUpdate.init(this)
        handleMalRedirect(intent)
        handleNotificationIntent(intent)
        setContent {
            val settings by Appearance.settings.collectAsStateWithLifecycle()
            val dark = rememberAppDarkTheme(settings.mode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose {}
            }
            // Preview a new UI scale live while the slider moves; a recreate then bakes it into the context.
            val baseDensity = LocalDensity.current
            val liveRatio = settings.uiScale / baseUiScale
            val density =
                remember(baseDensity, liveRatio) {
                    Density(baseDensity.density * liveRatio, baseDensity.fontScale)
                }
            CompositionLocalProvider(LocalDensity provides density) {
                AppTheme(settings = settings, darkTheme = dark) { App() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMalRedirect(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(AppUpdate.EXTRA_OPEN_UPDATE, false)) {
            intent.removeExtra(AppUpdate.EXTRA_OPEN_UPDATE)
            NotificationRouter.pendingOpenUpdate.value = true
            return
        }
        val id = intent.getStringExtra(EpisodeCheckWorker.EXTRA_OPEN_DETAIL) ?: return
        intent.removeExtra(EpisodeCheckWorker.EXTRA_OPEN_DETAIL)
        NotificationRouter.pendingDetail.value = id
    }

    private fun handleMalRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (Mal.isRedirect(uri)) Mal.handleRedirect(uri)
        if (AniList.isRedirect(uri)) AniList.handleRedirect(uri)
    }
}
