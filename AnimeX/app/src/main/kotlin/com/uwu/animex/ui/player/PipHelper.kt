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

/**
 * PiP helper (port dari Animiru).
 * AnimeX single-activity: flag [playerActive] agar MainActivity tahu kapan boleh auto-enter PiP.
 */
object PipHelper {
    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    /** True saat layar player sedang tampil (Compose). */
    @Volatile
    var playerActive: Boolean = false

    /** True jika video sedang play (bukan pause) — untuk pipOnExit. */
    @Volatile
    var isPlaying: Boolean = false

    fun isSupported(activity: Activity?): Boolean {
        if (activity == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return PlayerPrefs.enablePip.value
    }

    fun enter(activity: Activity?): Boolean {
        if (!isSupported(activity)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(PlayerPrefs.pipOnExit.value)
                        setSeamlessResizeEnabled(true)
                    }
                }
                .build()
            activity!!.enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false
        }
    }

    fun updateParams(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isSupported(activity)) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(PlayerPrefs.pipOnExit.value && playerActive)
                        setSeamlessResizeEnabled(true)
                    }
                }
                .build()
            activity.setPictureInPictureParams(params)
        } catch (_: Exception) {
        }
    }

    fun setInPip(value: Boolean) {
        _inPip.value = value
    }

    fun onUserLeaveHint(activity: ComponentActivity) {
        if (!playerActive) return
        if (!PlayerPrefs.pipOnExit.value) return
        if (!isPlaying) return
        if (!isSupported(activity)) return
        enter(activity)
    }
}
