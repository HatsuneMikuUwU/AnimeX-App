package com.uwu.animex.data.animein

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnimeinUser(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val coin: String? = null,
    val level: String? = null,
)

data class AnimeinAuthSession(
    val idUser: String,
    val keyClient: String,
    val user: AnimeinUser? = null,
)

/**
 * Sesi akun Animein (id_user + key_client) yang di-inject ke request API yang sama.
 */
object AnimeinSession {
    private const val PREFS = "animein_session"
    private const val KEY_SESSION = "session"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _session = MutableStateFlow<AnimeinAuthSession?>(null)
    val session: StateFlow<AnimeinAuthSession?> = _session.asStateFlow()

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _user = MutableStateFlow<AnimeinUser?>(null)
    val user: StateFlow<AnimeinUser?> = _user.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY_SESSION, null) ?: return
        val saved =
            runCatching {
                gson.fromJson(SecureStore.decrypt(raw), AnimeinAuthSession::class.java)
            }.getOrNull()
        if (saved != null && saved.idUser.isNotBlank() && saved.keyClient.isNotBlank()) {
            apply(saved)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun notify(msg: String) {
        _message.value = msg
    }

    val idUser: String?
        get() = _session.value?.idUser

    val keyClient: String?
        get() = _session.value?.keyClient

    fun isLoggedIn(): Boolean = _loggedIn.value

    /** Param umum yang ditambahkan interceptor Animein ke setiap request. */
    fun authParams(apkVer: String): Map<String, String> {
        val s = _session.value ?: return emptyMap()
        return buildMap {
            put("id_user", s.idUser)
            put("key_client", s.keyClient)
            if (apkVer.isNotBlank()) put("apk_ver", apkVer)
        }
    }

    @Synchronized
    fun save(session: AnimeinAuthSession) {
        apply(session)
        val enc = SecureStore.encrypt(gson.toJson(session))
        prefs?.edit()?.putString(KEY_SESSION, enc)?.apply()
    }

    @Synchronized
    fun updateUser(user: AnimeinUser) {
        val cur = _session.value ?: return
        val next = cur.copy(user = user)
        save(next)
    }

    @Synchronized
    fun logout() {
        _session.value = null
        _user.value = null
        _loggedIn.value = false
        prefs?.edit()?.remove(KEY_SESSION)?.apply()
    }

    private fun apply(session: AnimeinAuthSession) {
        _session.value = session
        _user.value = session.user
        _loggedIn.value = true
    }
}
