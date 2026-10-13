package com.uwu.animex.data.animein

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Logged-in ANIMEIN account. [key] is the server-issued client key; it is stored encrypted. */
data class AnimeInUser(
    val id: String,
    val key: String,
    val name: String?,
    val avatar: String?,
)

object AnimeInSession {
    private const val PREFS = "animein_session"
    private const val K_ID = "id"
    private const val K_KEY = "key"
    private const val K_NAME = "name"
    private const val K_AVATAR = "avatar"

    private var prefs: SharedPreferences? = null

    private val _user = MutableStateFlow<AnimeInUser?>(null)
    val user: StateFlow<AnimeInUser?> = _user.asStateFlow()

    val current: AnimeInUser? get() = _user.value

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val id = p.getString(K_ID, null)
        // A key that can no longer be decrypted (e.g. restored from backup) means logged out.
        val key = SecureStore.decrypt(p.getString(K_KEY, null))
        _user.value =
            if (id.isNullOrBlank() || key == null) {
                null
            } else {
                AnimeInUser(id, key, p.getString(K_NAME, null), p.getString(K_AVATAR, null))
            }
    }

    fun save(user: AnimeInUser) {
        prefs
            ?.edit()
            ?.putString(K_ID, user.id)
            ?.putString(K_KEY, SecureStore.encrypt(user.key))
            ?.putString(K_NAME, user.name)
            ?.putString(K_AVATAR, user.avatar)
            ?.apply()
        _user.value = user
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        _user.value = null
    }
}
