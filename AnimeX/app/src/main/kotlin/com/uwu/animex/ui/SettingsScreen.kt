package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen() {
    // bottom padding reserved so future content here also clears the floating nav pill
    Box(Modifier.fillMaxSize().padding(bottom = FloatingNavClearance))
}
