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
 *
 * Auto-enter / Home → PiP HANYA kalau:
 * - lagi di layar player ([playerActive])
 * - video lagi play ([isPlaying])
 * - pref enablePip ON
 *
 * Kalau cuma di home app / player di-pause / udah keluar player → jangan PiP.
 */
object PipHelper {
    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    /** True saat layar player sedang tampil (Compose). */
    @Volatile
    var playerActive: Boolean = false
        set(value) {
            field = value
            // pastiin auto-enter dimatiin pas keluar player
            lastActivity?.let { updateParams(it) }
        }

    /** True jika video sedang play (bukan pause). */
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

    /** Boleh auto-enter PiP pas tekan Home? */
    private fun shouldAutoEnter(): Boolean =
        PlayerPrefs.enablePip.value &&
            playerActive &&
            isPlaying

    fun enter(activity: Activity?): Boolean {
        if (!isSupported(activity)) return false
        // Manual dari tombol PiP: harus lagi di player
        if (!playerActive) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        lastActivity = activity
        return try {
            activity!!.enterPictureInPictureMode(buildParams(autoEnter = false))
        } catch (_: Exception) {
            false
        }
    }

    fun updateParams(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        lastActivity = activity
        try {
            // Selalu update params biar auto-enter ikut mati pas gak play / gak di player
            activity.setPictureInPictureParams(buildParams(autoEnter = shouldAutoEnter()))
        } catch (_: Exception) {
        }
    }

    private fun buildParams(autoEnter: Boolean): PictureInPictureParams {
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(autoEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    fun setInPip(value: Boolean) {
        _inPip.value = value
        if (!value) {
            // keluar dari PiP: pastikan auto-enter sesuai state terkini
            lastActivity?.let { updateParams(it) }
        }
    }

    fun onUserLeaveHint(activity: ComponentActivity) {
        // Android < 12: manual enter. Android 12+: auto-enter lewat params.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Cuma pastiin params up-to-date; sistem yang handle auto-enter
            updateParams(activity)
            return
        }
        if (!shouldAutoEnter()) return
        if (!isSupported(activity)) return
        enter(activity)
    }

    /** Panggil pas dispose player biar flag + auto-enter bersih. */
    fun onPlayerDisposed(activity: Activity?) {
        playerActive = false
        isPlaying = false
        updateParams(activity)
    }
}
