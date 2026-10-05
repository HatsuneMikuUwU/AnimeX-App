package com.uwu.animex.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.security.SecureStore
import com.uwu.animex.sync.providers.MALApi

object AccountManager {
    private const val PREFS = "accounts"
    private const val KEY_ACCOUNT = "account_"
    private const val KEY_PAYLOAD = "payload_"

    private val gson = Gson()
    private val accounts = HashMap<String, AuthData>()
    private var prefs: SharedPreferences? = null

    val malApi = SyncRepo(MALApi())

    val syncApis: List<SyncRepo>
        get() = listOf(malApi)

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        syncApis.forEach { repo ->
            val raw = p.getString(KEY_ACCOUNT + repo.idPrefix, null)
            val saved = runCatching {
                gson.fromJson(SecureStore.decrypt(raw), AuthData::class.java)
            }.getOrNull()
            if (saved != null) {
                accounts[repo.idPrefix] = saved

                if (raw != null && !SecureStore.isEncrypted(raw)) {
                    p.edit().putString(KEY_ACCOUNT + repo.idPrefix, SecureStore.encrypt(raw)).apply()
                }
            }
        }
    }

    @Synchronized
    fun authData(idPrefix: String): AuthData? = accounts[idPrefix]

    @Synchronized
    fun save(idPrefix: String, data: AuthData) {
        accounts[idPrefix] = data
        prefs?.edit()?.putString(KEY_ACCOUNT + idPrefix, SecureStore.encrypt(gson.toJson(data)))?.apply()
    }

    @Synchronized
    fun remove(idPrefix: String) {
        accounts.remove(idPrefix)
        prefs?.edit()?.remove(KEY_ACCOUNT + idPrefix)?.remove(KEY_PAYLOAD + idPrefix)?.apply()
    }

    @Synchronized
    fun savePayload(idPrefix: String, payload: String?) {
        prefs?.edit()?.apply {
            if (payload == null) remove(KEY_PAYLOAD + idPrefix) else putString(KEY_PAYLOAD + idPrefix, SecureStore.encrypt(payload))
        }?.apply()
    }

    @Synchronized
    fun payload(idPrefix: String): String? = SecureStore.decrypt(prefs?.getString(KEY_PAYLOAD + idPrefix, null))
}
