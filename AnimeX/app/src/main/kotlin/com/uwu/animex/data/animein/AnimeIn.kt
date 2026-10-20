package com.uwu.animex.data.animein

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.security.SecureStore
import com.uwu.animex.data.api.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

object AnimeIn {
    const val GOOGLE_SERVER_CLIENT_ID = "49740216870-nuusdrbp4586715nttl6gnjged1d7pn6.apps.googleusercontent.com"
    const val APK_VER = "5.2.2"

    private const val PREFS = "animein_account"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_KEY_CLIENT = "key_client"
    private const val KEY_USER_JSON = "user_json"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val http get() = NetworkModule.apiClient
    private var prefs: SharedPreferences? = null

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _keyClient = MutableStateFlow<String?>(null)
    val keyClient: StateFlow<String?> = _keyClient.asStateFlow()

    private val _user = MutableStateFlow<AnimeInUser?>(null)
    val user: StateFlow<AnimeInUser?> = _user.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun setMessage(msg: String) {
        _message.value = msg
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p

        val savedUid = p.getString(KEY_USER_ID, null)?.let { SecureStore.decrypt(it) }
        val savedKey = p.getString(KEY_KEY_CLIENT, null)?.let { SecureStore.decrypt(it) }
        val savedUser = p.getString(KEY_USER_JSON, null)?.let { SecureStore.decrypt(it) }

        if (!savedUid.isNullOrBlank() && !savedKey.isNullOrBlank()) {
            _userId.value = savedUid
            _keyClient.value = savedKey
            _loggedIn.value = true
            if (!savedUser.isNullOrBlank()) {
                _user.value = runCatching { gson.fromJson(savedUser, AnimeInUser::class.java) }.getOrNull()
            }
            scope.launch {
                runCatching { refreshUser() }
            }
        }
    }

    private fun getBaseUrl(): String {
        val base = Api.baseUrl.takeIf { it.isNotBlank() } ?: "https://xyz-api.animein.net/"
        return if (base.endsWith("/")) base else "$base/"
    }

    suspend fun refreshUser(): Boolean =
        withContext(Dispatchers.IO) {
            val uid = _userId.value ?: return@withContext false
            val key = _keyClient.value ?: return@withContext false
            val base = getBaseUrl()

            try {
                val url =
                    (base + "3/2/user/profile/data")
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("id_user", uid)
                        .addQueryParameter("key_client", key)
                        .addQueryParameter("apk_ver", APK_VER)
                        .build()

                val req = Request.Builder().url(url).build()
                val res =
                    http.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) return@withContext false
                        response.body.string()
                    }

                val envelope = gson.fromJson(res, AnimeInProfileEnvelope::class.java)
                val profile = envelope?.data?.user ?: return@withContext false
                val isPro = envelope.data.pro || profile.pro
                val isAdmin = envelope.data.admin || profile.admin
                val fullUser = profile.copy(pro = isPro, admin = isAdmin)

                _user.value = fullUser
                prefs?.edit()?.putString(KEY_USER_JSON, SecureStore.encrypt(gson.toJson(fullUser)))?.apply()
                true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun loginWithGoogle(
        email: String,
        idToken: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            _busy.value = true
            try {
                val base = getBaseUrl()
                val body =
                    FormBody
                        .Builder()
                        .add("email", email)
                        .add("token", idToken)
                        .build()

                val req =
                    Request
                        .Builder()
                        .url(base + "auth/google")
                        .post(body)
                        .build()

                val res =
                    http.newCall(req).execute().use { response ->
                        response.body.string()
                    }

                val env = gson.fromJson(res, AnimeInAuthEnvelope::class.java)
                if (env == null || env.error) {
                    val err = env?.message ?: env?.data?.message ?: "Gagal login dengan Google"
                    _message.value = err
                    return@withContext Result.failure(Exception(err))
                }

                val authUser = env.data?.user
                val uid = authUser?.id
                val key = authUser?.key_client

                if (uid.isNullOrBlank() || key.isNullOrBlank()) {
                    val err = "Akun belum terdaftar di Animein. Silakan buka aplikasi Animein untuk registrasi awal."
                    _message.value = err
                    return@withContext Result.failure(Exception(err))
                }

                saveSession(uid, key)
                refreshUser()
                _message.value = "Berhasil masuk sebagai ${_user.value?.username?.takeIf { it.isNotBlank() } ?: email}"
                Result.success(true)
            } catch (e: Exception) {
                val msg = e.toUserMessage()
                _message.value = "Login gagal: $msg"
                Result.failure(e)
            } finally {
                _busy.value = false
            }
        }

    suspend fun loginWithCredentials(
        usernameOrEmail: String,
        pass: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            _busy.value = true
            try {
                val base = getBaseUrl()
                val body =
                    FormBody
                        .Builder()
                        .add("username_or_email", usernameOrEmail)
                        .add("password", pass)
                        .build()

                val req =
                    Request
                        .Builder()
                        .url(base + "auth/login")
                        .post(body)
                        .build()

                val res =
                    http.newCall(req).execute().use { response ->
                        response.body.string()
                    }

                val env = gson.fromJson(res, AnimeInAuthEnvelope::class.java)
                if (env == null || env.error) {
                    val err = env?.message ?: env?.data?.message ?: "Password atau username salah"
                    _message.value = err
                    return@withContext Result.failure(Exception(err))
                }

                val authUser = env.data?.user
                val uid = authUser?.id
                val key = authUser?.key_client

                if (uid.isNullOrBlank() || key.isNullOrBlank()) {
                    val err = env.data?.message ?: "Kredensial login tidak valid"
                    _message.value = err
                    return@withContext Result.failure(Exception(err))
                }

                saveSession(uid, key)
                refreshUser()
                _message.value = "Berhasil masuk sebagai ${_user.value?.username?.takeIf { it.isNotBlank() } ?: usernameOrEmail}"
                Result.success(true)
            } catch (e: Exception) {
                val msg = e.toUserMessage()
                _message.value = "Login gagal: $msg"
                Result.failure(e)
            } finally {
                _busy.value = false
            }
        }

    suspend fun loginWithSession(
        uid: String,
        key: String,
    ): Result<Boolean> =
        withContext(Dispatchers.IO) {
            _busy.value = true
            try {
                saveSession(uid.trim(), key.trim())
                val ok = refreshUser()
                if (!ok) {
                    logout()
                    val err = "ID User atau Key Client tidak valid"
                    _message.value = err
                    return@withContext Result.failure(Exception(err))
                }
                _message.value = "Berhasil masuk sebagai ${_user.value?.username?.takeIf { it.isNotBlank() } ?: uid}"
                Result.success(true)
            } catch (e: Exception) {
                val msg = e.toUserMessage()
                _message.value = "Gagal: $msg"
                Result.failure(e)
            } finally {
                _busy.value = false
            }
        }

    private fun saveSession(
        uid: String,
        key: String,
    ) {
        _userId.value = uid
        _keyClient.value = key
        _loggedIn.value = true
        prefs
            ?.edit()
            ?.putString(KEY_USER_ID, SecureStore.encrypt(uid))
            ?.putString(KEY_KEY_CLIENT, SecureStore.encrypt(key))
            ?.apply()
    }

    fun logout() {
        _loggedIn.value = false
        _userId.value = null
        _keyClient.value = null
        _user.value = null
        prefs
            ?.edit()
            ?.remove(KEY_USER_ID)
            ?.remove(KEY_KEY_CLIENT)
            ?.remove(KEY_USER_JSON)
            ?.apply()
        _message.value = "Telah keluar dari Animein"
    }
}
