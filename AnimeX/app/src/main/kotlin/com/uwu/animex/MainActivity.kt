package com.uwu.animex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.uwu.animex.data.History
import com.uwu.animex.data.Progress
import com.uwu.animex.ui.AnimeinTheme
import com.uwu.animex.ui.ThemeController
import com.uwu.animex.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        History.init(this)
        Progress.init(this)
        setContent {
            val themeController = remember { ThemeController(applicationContext) }
            AnimeinTheme(themeController) { App() }
        }
    }
}
