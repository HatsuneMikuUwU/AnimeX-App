package com.uwu.animex.security

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.uwu.animex.BuildConfig
import java.security.MessageDigest

/**
 * Runtime signing-certificate verification. Confirms the running APK
 * was signed with the expected release key, so a repackaged/resigned
 * copy can be told apart from the real build. Not unbeatable — this
 * check itself can be patched out of the bytecode by a determined
 * reverse engineer — but combined with R8 full mode + repackaging it
 * has to be found first.
 *
 * Call [performChecks] as the very first thing in MainActivity's
 * onCreate, before installSplashScreen/setContent.
 */
object SecurityGuard {

    // Diisi OTOMATIS oleh app/build.gradle.kts (releaseSignatureCipherOrBlank())
    // dari keystore rilis yang lagi dipakai (via env KEYSTORE_FILE/
    // KEYSTORE_PASSWORD/KEY_ALIAS) setiap kali build — tidak perlu
    // hitung/encode manual lagi. Kosong pada debug build atau saat
    // env keystore belum di-set, dan itu berarti check di-skip
    // (lihat isSignatureValid), bukan gagal.
    private val EXPECTED_SIGNATURE_SHA256 = Obf.d(BuildConfig.SIG_CIPHER)

    fun performChecks(activity: Activity) {
        val suspicious = !isSignatureValid(activity)

        if (suspicious) {
            // No dialog, no toast: don't tell an attacker what caught
            // them. Just disappear.
            activity.finishAffinity()
            Runtime.getRuntime().exit(0)
        }
    }

    /**
     * Nilai pembanding (EXPECTED_SIGNATURE_SHA256) sudah otomatis
     * dihitung dari keystore rilis oleh app/build.gradle.kts setiap
     * kali build — lihat releaseSignatureCipherOrBlank() di sana.
     * Tidak ada langkah manual lagi. Kalau env keystore (KEYSTORE_FILE
     * dkk) belum di-set, nilainya kosong dan check ini otomatis
     * di-skip supaya tidak mengunci build lokal/debug.
     */
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
