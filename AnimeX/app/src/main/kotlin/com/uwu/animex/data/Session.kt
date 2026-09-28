package com.uwu.animex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.mmkv.MMKV

/**
 * Auth session (ANIMEIN SessionManager-style).
 * Stores access_token, user_id, profile fields in MMKV.
 */
object Session {
    private const val K_TOKEN = "access_token"
    private const val K_USER_ID = "user_id"
    private const val K_USERNAME = "username"
    private const val K_EMAIL = "email"
    private const val K_AVATAR = "avatar"
    private const val K_LOGIN_TYPE = "login_type" // email | google

    private var kv: MMKV? = null

    var accessToken: String by mutableStateOf("")
        private set
    var userId: String by mutableStateOf("")
        private set
    var username: String by mutableStateOf("")
        private set
    var email: String by mutableStateOf("")
        private set
    var avatar: String by mutableStateOf("")
        private set
    var loginType: String by mutableStateOf("")
        private set

    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank() && userId.isNotBlank()

    fun init(context: Context) {
        if (kv != null) return
        MmkvStore.init(context)
        val mmkv = MmkvStore.user()
        kv = mmkv
        accessToken = mmkv.decodeString(K_TOKEN, "").orEmpty()
        userId = mmkv.decodeString(K_USER_ID, "").orEmpty()
        username = mmkv.decodeString(K_USERNAME, "").orEmpty()
        email = mmkv.decodeString(K_EMAIL, "").orEmpty()
        avatar = mmkv.decodeString(K_AVATAR, "").orEmpty()
        loginType = mmkv.decodeString(K_LOGIN_TYPE, "").orEmpty()
    }

    fun saveLogin(
        token: String,
        userId: String,
        username: String = "",
        email: String = "",
        avatar: String = "",
        loginType: String = "email",
    ) {
        this.accessToken = token
        this.userId = userId
        this.username = username
        this.email = email
        this.avatar = avatar
        this.loginType = loginType
        kv?.apply {
            encode(K_TOKEN, token)
            encode(K_USER_ID, userId)
            encode(K_USERNAME, username)
            encode(K_EMAIL, email)
            encode(K_AVATAR, avatar)
            encode(K_LOGIN_TYPE, loginType)
        }
    }

    fun updateProfile(username: String? = null, email: String? = null, avatar: String? = null) {
        if (username != null) {
            this.username = username
            kv?.encode(K_USERNAME, username)
        }
        if (email != null) {
            this.email = email
            kv?.encode(K_EMAIL, email)
        }
        if (avatar != null) {
            this.avatar = avatar
            kv?.encode(K_AVATAR, avatar)
        }
    }

    fun logout() {
        accessToken = ""
        userId = ""
        username = ""
        email = ""
        avatar = ""
        loginType = ""
        kv?.apply {
            removeValueForKey(K_TOKEN)
            removeValueForKey(K_USER_ID)
            removeValueForKey(K_USERNAME)
            removeValueForKey(K_EMAIL)
            removeValueForKey(K_AVATAR)
            removeValueForKey(K_LOGIN_TYPE)
        }
    }
}
