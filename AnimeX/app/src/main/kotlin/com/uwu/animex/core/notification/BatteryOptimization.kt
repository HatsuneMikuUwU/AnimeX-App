package com.uwu.animex.core.notification

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.uwu.animex.core.storage.PrefKeys

object BatteryOptimization {
    fun isRestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun shouldPrompt(context: Context): Boolean = isRestricted(context) && !isDismissed(context)

    fun markDismissed(context: Context) {
        context
            .getSharedPreferences(PrefKeys.BatteryOptimization.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefKeys.BatteryOptimization.DISMISSED, true)
            .apply()
    }

    fun requestIgnore(context: Context) {
        val direct =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        } catch (_: SecurityException) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun isDismissed(context: Context): Boolean =
        context
            .getSharedPreferences(PrefKeys.BatteryOptimization.FILE, Context.MODE_PRIVATE)
            .getBoolean(PrefKeys.BatteryOptimization.DISMISSED, false)
}
