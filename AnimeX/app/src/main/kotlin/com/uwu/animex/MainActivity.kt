package com.uwu.animex

import android.graphics.Color
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.uwu.animex.data.ApiSettings
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.EpisodeAlerts
import com.uwu.animex.data.History
import com.uwu.animex.data.Mal
import com.uwu.animex.data.Onboarding
import com.uwu.animex.data.Progress
import com.uwu.animex.data.SearchHistory
import com.uwu.animex.ui.App
import com.uwu.animex.ui.AppTheme
import com.uwu.animex.ui.NotificationRouter

class MainActivity : ComponentActivity() {
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
        ApiSettings.init(this)
        Onboarding.init(this)
        History.init(this)
        Progress.init(this)
        Bookmarks.init(this)
        SearchHistory.init(this)
        Downloads.init(this)
        Mal.init(this)
        EpisodeAlerts.init(this)
        handleMalRedirect(intent)
        handleNotificationIntent(intent)
        setContent { AppTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMalRedirect(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EpisodeCheckWorker.EXTRA_OPEN_DETAIL) ?: return
        intent.removeExtra(EpisodeCheckWorker.EXTRA_OPEN_DETAIL)
        NotificationRouter.pendingDetail.value = id
    }

    private fun handleMalRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (Mal.isRedirect(uri)) Mal.handleRedirect(uri)
    }
}
