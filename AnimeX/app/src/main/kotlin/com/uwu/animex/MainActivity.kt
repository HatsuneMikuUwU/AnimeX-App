package com.uwu.animex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.History
import com.uwu.animex.data.Progress
import com.uwu.animex.ui.App
import com.uwu.animex.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        History.init(this)
        Progress.init(this)
        Bookmarks.init(this)
        setContent { AppTheme { App() } }
    }
}
