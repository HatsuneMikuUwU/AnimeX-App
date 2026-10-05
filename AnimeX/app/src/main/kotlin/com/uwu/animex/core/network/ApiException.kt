package com.uwu.animex.core.network

import com.google.gson.JsonParseException
import com.uwu.animex.sync.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/**
 * Typed network failures so the UI can tell "offline" from "server down" from "bad data"
 * instead of showing one generic error for everything.
 */
sealed class ApiException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Offline(cause: Throwable? = null) : ApiException("Offline", cause)
    class Timeout(cause: Throwable? = null) : ApiException("Timeout", cause)
    class Unreachable(cause: Throwable? = null) : ApiException("Unreachable", cause)
    class Blocked(cause: Throwable? = null) : ApiException("Cleartext blocked", cause)
    class Tls(cause: Throwable? = null) : ApiException("TLS error", cause)
    class Http(val code: Int) : ApiException("HTTP $code")
    class Parse(cause: Throwable? = null) : ApiException("Parse error", cause)

    /** Error reported by the API itself (envelope `error = true`) or a missing result. */
    class Remote(message: String) : ApiException(message)

    /** Build/config problem (missing secrets, etc). */
    class Config(message: String) : ApiException(message)
}

/** Maps low level IO failures onto [ApiException]. */
fun IOException.toApiException(): ApiException = when {
    this is ApiException -> this
    this is UnknownServiceException -> ApiException.Blocked(this)
    !ConnectivityMonitor.isOnline() -> ApiException.Offline(this)
    this is SocketTimeoutException -> ApiException.Timeout(this)
    this is SSLException -> ApiException.Tls(this)
    this is UnknownHostException || this is ConnectException -> ApiException.Unreachable(this)
    else -> ApiException.Unreachable(this)
}

/** Friendly (Indonesian) message that is safe to show in the UI. */
fun Throwable.toUserMessage(): String = when (this) {
    is ApiException.Offline -> "Kamu lagi offline nih. Cek koneksi internetmu dulu ya."
    is ApiException.Timeout -> "Koneksinya lemot banget, servernya kelamaan jawab."
    is ApiException.Unreachable -> "Gak bisa nyambung ke server. Coba lagi bentar ya."
    is ApiException.Blocked -> "Server ini cuma dukung HTTP (gak aman), jadi diblokir."
    is ApiException.Tls -> "Sertifikat keamanan servernya bermasalah, koneksi dibatalin."
    is ApiException.Parse -> "Data dari server berantakan, coba lagi nanti ya."
    is ApiException.Http -> httpMessage(code)
    is HttpException -> httpMessage(code)
    is ApiException.Remote -> message ?: "Server ngasih error."
    is ApiException.Config -> message ?: "Konfigurasi aplikasi belum lengkap."
    is JsonParseException -> "Data dari server berantakan, coba lagi nanti ya."
    is IOException -> "Gangguan jaringan, coba lagi ya."
    else -> message?.takeIf { it.isNotBlank() } ?: "Waduh, ada yang error nih"
}

private fun httpMessage(code: Int): String = when {
    code == 404 -> "Datanya gak ketemu (404)."
    code == 429 -> "Servernya lagi rame banget, coba lagi bentar ya."
    code in 500..599 -> "Servernya lagi bermasalah (HTTP $code)."
    else -> "Server nolak permintaannya (HTTP $code)."
}

/**
 * Like [runCatching] but never swallows coroutine cancellation, which would otherwise
 * break structured concurrency (a cancelled screen would keep firing fallback requests).
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
