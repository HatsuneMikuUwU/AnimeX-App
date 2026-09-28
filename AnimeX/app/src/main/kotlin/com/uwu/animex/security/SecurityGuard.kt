package com.uwu.animex.security

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.uwu.animex.BuildConfig
import java.security.MessageDigest

object SecurityGuard {

    private val EXPECTED_SIGNATURE_SHA256 = Obf.d(BuildConfig.SIG_CIPHER)

    fun performChecks(activity: Activity) {
        val suspicious = !isSignatureValid(activity)

        if (suspicious) {
            activity.finishAffinity()
            Runtime.getRuntime().exit(0)
        }
    }

    private fun isSignatureValid(context: Context): Boolean {
        if (EXPECTED_SIGNATURE_SHA256.isBlank()) return true
        val actual = currentSignatureSha256(context) ?: return false
        return actual.equals(EXPECTED_SIGNATURE_SHA256, ignoreCase = true)
    }

    private fun currentSignatureSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val signingInfo = info.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val sig = signatures?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
        digest.joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
