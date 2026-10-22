package com.uwu.animex.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.security.SecureStore
import com.uwu.animex.sync.providers.AniListApi
import com.uwu.animex.sync.providers.MALApi

object AccountManager {
    private const val PREFS = "accounts"
    private const val KEY_ACCOUNT = "account_"
    private const val KEY_PAYLOAD = "payload_"

    private val gson = Gson()
    private val accounts = HashMap<String, AuthData>()
    private var prefs: SharedPreferences? = null

    val malApi = SyncRepo(MALApi())
    val aniListApi = SyncRepo(AniListApi())

    val syncApis: List<SyncRepo>
        get() = listOf(malApi, aniListApi)

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        syncApis.forEach { repo ->
            val payloadKey = KEY_PAYLOAD + repo.idPrefix
            val legacyPayload = p.getString(payloadKey, null)
            if (legacyPayload != null && !SecureStore.isEncrypted(legacyPayload)) {
                runCatching { SecureStore.encrypt(legacyPayload) }
                    .onSuccess { p.edit().putString(payloadKey, it).apply() }
                    .onFailure { p.edit().remove(payloadKey).apply() }
            }
            val raw = p.getString(KEY_ACCOUNT + repo.idPrefix, null)
            val secureRaw =
                if (raw != null && !SecureStore.isEncrypted(raw)) {
                    runCatching { SecureStore.encrypt(raw) }
                        .onSuccess { p.edit().putString(KEY_ACCOUNT + repo.idPrefix, it).apply() }
                        .onFailure { p.edit().remove(KEY_ACCOUNT + repo.idPrefix).apply() }
                        .getOrNull()
                } else {
                    raw
                }
            val saved =
                runCatching {
                    gson.fromJson(SecureStore.decrypt(secureRaw), AuthData::class.java)
                }.getOrNull()
            if (saved != null) {
                accounts[repo.idPrefix] = saved
            }
        }
    }

    @Synchronized
    fun authData(idPrefix: String): AuthData? = accounts[idPrefix]

    @Synchronized
    fun save(
        idPrefix: String,
        data: AuthData,
    ) {
        val encrypted = SecureStore.encrypt(gson.toJson(data))
        prefs?.edit()?.putString(KEY_ACCOUNT + idPrefix, encrypted)?.apply()
        accounts[idPrefix] = data
    }

    @Synchronized
    fun remove(idPrefix: String) {
        accounts.remove(idPrefix)
        prefs
            ?.edit()
            ?.remove(KEY_ACCOUNT + idPrefix)
            ?.remove(KEY_PAYLOAD + idPrefix)
            ?.apply()
    }

    @Synchronized
    fun savePayload(
        idPrefix: String,
        payload: String?,
    ) {
        val encrypted = payload?.let(SecureStore::encrypt)
        prefs
            ?.edit()
            ?.apply {
                if (encrypted == null) {
                    remove(KEY_PAYLOAD + idPrefix)
                } else {
                    putString(KEY_PAYLOAD + idPrefix, encrypted)
                }
            }?.apply()
    }

    @Synchronized
    fun payload(idPrefix: String): String? = SecureStore.decrypt(prefs?.getString(KEY_PAYLOAD + idPrefix, null))
}
