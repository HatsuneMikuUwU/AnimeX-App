package com.uwu.animex.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import com.uwu.animex.data.local.PlayerPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PipHelper {
    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    @Volatile
    var playerActive: Boolean = false
        set(value) {
            field = value
            lastActivity?.let { updateParams(it) }
        }

    @Volatile
    var isPlaying: Boolean = false
        set(value) {
            field = value
            lastActivity?.let { updateParams(it) }
        }

    @Volatile
    private var lastActivity: Activity? = null

    fun isSupported(activity: Activity?): Boolean {
        if (activity == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return PlayerPrefs.enablePip.value
    }

    private fun shouldAutoEnter(): Boolean =
        PlayerPrefs.enablePip.value &&
            playerActive &&
            isPlaying

    fun enter(activity: Activity?): Boolean {
        if (!isSupported(activity)) return false
        if (!playerActive) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val host = activity ?: return false
        lastActivity = host
        return try {
            host.enterPictureInPictureMode(buildParams(autoEnter = false))
        } catch (_: Exception) {
            false
        }
    }

    fun updateParams(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        lastActivity = activity
        try {
            activity.setPictureInPictureParams(buildParams(autoEnter = shouldAutoEnter()))
        } catch (_: Exception) {
        }
    }

    private fun buildParams(autoEnter: Boolean): PictureInPictureParams {
        return PictureInPictureParams
            .Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }.build()
    }

    fun setInPip(value: Boolean) {
        _inPip.value = value
        if (!value) {
            lastActivity?.let { updateParams(it) }
        }
    }

    fun onUserLeaveHint(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateParams(activity)
            return
        }
        if (!shouldAutoEnter()) return
        if (!isSupported(activity)) return
        enter(activity)
    }

    fun onPlayerDisposed(activity: Activity?) {
        playerActive = false
        isPlaying = false
        updateParams(activity)
    }
}
