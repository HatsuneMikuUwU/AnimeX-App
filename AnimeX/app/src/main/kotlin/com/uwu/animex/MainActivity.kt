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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Onboarding
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.update.AppUpdate
import com.uwu.animex.ui.navigation.App
import com.uwu.animex.ui.navigation.NotificationRouter
import com.uwu.animex.ui.theme.AppTheme
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.theme.withUiScale
import com.uwu.animex.work.EpisodeCheckWorker

class MainActivity : ComponentActivity() {
    /**
     * Skala antarmuka dipasang di context activity, jadi dp/sp di semua window (dialog, sheet, menu)
     * memakai density yang sama. Perubahan skala baru berlaku di attach berikutnya (activity di-recreate).
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withUiScale(Appearance.readUiScale(newBase)))
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
        Onboarding.init(this)
        History.init(this)
        Progress.init(this)
        Bookmarks.init(this)
        SearchHistory.init(this)
        Downloads.init(this)
        Mal.init(this)
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
            AppTheme(settings = settings, darkTheme = dark) { App() }
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
    }
}
