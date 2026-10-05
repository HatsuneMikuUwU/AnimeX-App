package com.uwu.animex.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.security.SecureStore
import com.uwu.animex.sync.providers.MALApi

object AccountManager {
    private const val PREFS = "accounts"
    private const val LEGACY_PREFS = "mal"
    private const val KEY_ACCOUNT = "account_"
    private const val KEY_PAYLOAD = "payload_"

    private val gson = Gson()
    private val accounts = HashMap<String, AuthData>()
    private var prefs: SharedPreferences? = null

    val malApi = SyncRepo(MALApi())

    val syncApis: List<SyncRepo>
        get() = listOf(malApi)

    private data class LegacyUser(
        val id: Long? = null,
        val name: String? = null,
        val picture: String? = null,
    )

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
                // Tokens saved by older versions are plain text: encrypt them in place.
                if (raw != null && !SecureStore.isEncrypted(raw)) {
                    p.edit().putString(KEY_ACCOUNT + repo.idPrefix, SecureStore.encrypt(raw)).apply()
                }
            }
        }
        migrateLegacy(app)
    }

    private fun migrateLegacy(context: Context) {
        if (accounts.containsKey(malApi.idPrefix)) return
        val old = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val refresh = old.getString("refresh", null) ?: return
        val user = runCatching {
            gson.fromJson(old.getString("user", null), LegacyUser::class.java)
        }.getOrNull() ?: return
        val id = user.id?.toInt() ?: return
        val token = AuthToken(
            accessToken = old.getString("access", null),
            refreshToken = refresh,
            accessTokenLifetime = old.getLong("expires", 0L) / 1000L,
        )
        save(malApi.idPrefix, AuthData(AuthUser(user.name, id, user.picture), token))
        old.edit().remove("access").remove("refresh").remove("expires").apply()
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
