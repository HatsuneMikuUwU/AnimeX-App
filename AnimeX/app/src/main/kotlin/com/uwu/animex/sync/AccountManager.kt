package com.uwu.animex.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.security.SecureStore
import com.uwu.animex.core.storage.PrefKeys
import com.uwu.animex.sync.anilist.AniListApi
import com.uwu.animex.sync.mal.MALApi

object AccountManager {
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
        val p = app.getSharedPreferences(PrefKeys.Accounts.FILE, Context.MODE_PRIVATE)
        prefs = p
        syncApis.forEach { repo ->
            val raw = p.getString(PrefKeys.Accounts.ACCOUNT_PREFIX + repo.idPrefix, null)
            val saved =
                runCatching {
                    gson.fromJson(SecureStore.decrypt(raw), AuthData::class.java)
                }.getOrNull()
            if (saved != null) {
                accounts[repo.idPrefix] = saved

                if (raw != null && !SecureStore.isEncrypted(raw)) {
                    SecureStore.encrypt(raw)?.let {
                        p.edit().putString(PrefKeys.Accounts.ACCOUNT_PREFIX + repo.idPrefix, it).apply()
                    }
                }
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
        accounts[idPrefix] = data
        SecureStore.encrypt(gson.toJson(data))?.let {
            prefs?.edit()?.putString(PrefKeys.Accounts.ACCOUNT_PREFIX + idPrefix, it)?.apply()
        }
    }

    @Synchronized
    fun remove(idPrefix: String) {
        accounts.remove(idPrefix)
        prefs
            ?.edit()
            ?.remove(PrefKeys.Accounts.ACCOUNT_PREFIX + idPrefix)
            ?.remove(PrefKeys.Accounts.PAYLOAD_PREFIX + idPrefix)
            ?.apply()
    }

    @Synchronized
    fun savePayload(
        idPrefix: String,
        payload: String?,
    ) {
        prefs
            ?.edit()
            ?.apply {
                if (payload == null) {
                    remove(PrefKeys.Accounts.PAYLOAD_PREFIX + idPrefix)
                } else {
                    SecureStore.encrypt(payload)?.let { putString(PrefKeys.Accounts.PAYLOAD_PREFIX + idPrefix, it) }
                }
            }?.apply()
    }

    @Synchronized
    fun payload(idPrefix: String): String? =
        SecureStore.decrypt(prefs?.getString(PrefKeys.Accounts.PAYLOAD_PREFIX + idPrefix, null))
}
