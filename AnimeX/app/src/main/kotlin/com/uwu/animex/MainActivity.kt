package com.uwu.animex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uwu.animex.data.History
import com.uwu.animex.ui.AnimeinTheme
import com.uwu.animex.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        History.init(this)
        setContent { AnimeinTheme { App() } }
    }
}
