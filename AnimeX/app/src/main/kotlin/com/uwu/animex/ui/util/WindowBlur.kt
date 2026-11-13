package com.uwu.animex.ui.util

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import java.util.function.Consumer

private const val DefaultBlurRadius = 10

@Composable
fun WindowBlurEffect(
    blurRadius: Int = DefaultBlurRadius,
    enabled: Boolean = true,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    val systemAllows = rememberCrossWindowBlurEnabled()
    val active = enabled && settings.blur && systemAllows

    val radius = if (active) blurRadius else 0

    DisposableEffect(view, radius) {
        val apply = Runnable { view.setWindowBlur(radius) }
        if (view.rootView.isAttachedToWindow) apply.run() else view.post(apply)
        onDispose { view.removeCallbacks(apply) }
    }
    DisposableEffect(view) {
        onDispose { view.setWindowBlur(0) }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun rememberCrossWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    val wm = remember(context) { context.getSystemService(WindowManager::class.java) }
    var enabled by remember { mutableStateOf(wm.isCrossWindowBlurEnabled) }
    DisposableEffect(wm) {
        val listener = Consumer<Boolean> { enabled = it }
        wm.addCrossWindowBlurEnabledListener(listener)
        onDispose { wm.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

@RequiresApi(Build.VERSION_CODES.S)
private fun View.setWindowBlur(radius: Int) {
    val dialogWindow = (this as? DialogWindowProvider)?.window ?: (parent as? DialogWindowProvider)?.window
    if (dialogWindow != null) {
        dialogWindow.setBlur(radius)
        return
    }
    val root = rootView
    val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
    if (!root.isAttachedToWindow) return
    lp.applyBlur(radius)
    runCatching { context.getSystemService(WindowManager::class.java).updateViewLayout(root, lp) }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.setBlur(radius: Int) {
    val lp = attributes
    lp.applyBlur(radius)
    attributes = lp
}

@RequiresApi(Build.VERSION_CODES.S)
private fun WindowManager.LayoutParams.applyBlur(radius: Int) {
    if (radius > 0) {
        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        blurBehindRadius = radius.coerceIn(0, 150)
    } else {
        flags = flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        blurBehindRadius = 0
    }
}
