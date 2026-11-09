package com.uwu.animex.data.user

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    @SerializedName("id") val id: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("key_client") val keyClient: String? = null,
    @SerializedName("money") val money: Long? = null,
    @SerializedName("coin") val coin: Long? = null,
    @SerializedName("default_account") val isDefaultAccount: Boolean = false,
) {
    val isAuthenticated: Boolean
        get() = !id.isNullOrBlank() && !keyClient.isNullOrBlank()
}

object UserSession {
    private const val PREFS = "user_session"
    private const val KEY_USER = "user"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _user = MutableStateFlow<UserProfile?>(null)
    val user: StateFlow<UserProfile?> = _user.asStateFlow()

    val loggedIn: StateFlow<Boolean>
        get() = MutableStateFlow(_user.value?.isAuthenticated == true)

    fun isLoggedIn(): Boolean = _user.value?.isAuthenticated == true

    fun userId(): String? = _user.value?.id?.takeIf { it.isNotBlank() }

    fun keyClient(): String? = _user.value?.keyClient?.takeIf { it.isNotBlank() }

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY_USER, null)
        val saved =
            runCatching {
                gson.fromJson(SecureStore.decrypt(raw), UserProfile::class.java)
            }.getOrNull()
        if (saved != null && saved.isAuthenticated) {
            _user.value = saved
            if (raw != null && !SecureStore.isEncrypted(raw)) {
                SecureStore.encrypt(raw)?.let {
                    p.edit().putString(KEY_USER, it).apply()
                }
            }
        }
    }

    @Synchronized
    fun save(profile: UserProfile) {
        _user.value = profile
        SecureStore.encrypt(gson.toJson(profile))?.let {
            prefs?.edit()?.putString(KEY_USER, it)?.apply()
        }
    }

    @Synchronized
    fun update(block: (UserProfile) -> UserProfile) {
        val cur = _user.value ?: return
        save(block(cur))
    }

    @Synchronized
    fun clear() {
        _user.value = null
        prefs?.edit()?.remove(KEY_USER)?.apply()
    }
}
