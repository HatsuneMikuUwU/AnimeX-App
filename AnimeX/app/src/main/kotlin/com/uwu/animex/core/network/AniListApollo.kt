package com.uwu.animex.core.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.network.okHttpClient
import com.uwu.animex.sync.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Apollo GraphQL client for AniList, patterned after AniHyou's NetworkModule.
 *
 * - Bearer token is injected via [AuthorizationInterceptor]
 * - Referer header is required by AniList for third-party clients
 */
object AniListApollo {
    const val GRAPHQL_URL = "https://graphql.anilist.co"
    const val ANILIST_URL = "https://anilist.co"

    private const val DEFAULT_RETRIES = 2
    private const val RETRY_DELAY_MS = 800L
    private const val INVALID_TOKEN = "Invalid token"

    private val accessToken = AtomicReference<String?>(null)

    fun setAccessToken(token: String?) {
        accessToken.set(token)
    }

    fun currentAccessToken(): String? = accessToken.get()

    val client: ApolloClient by lazy {
        val okHttp =
            NetworkModule.client
                .newBuilder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor(RefererInterceptor())
                .addInterceptor(AuthorizationInterceptor())
                .build()

        ApolloClient
            .Builder()
            .serverUrl(GRAPHQL_URL)
            .okHttpClient(okHttp)
            .httpExposeErrorBody(true)
            .build()
    }

    suspend fun <D : Operation.Data> execute(
        token: String?,
        tolerateErrors: Boolean = false,
        retries: Int = DEFAULT_RETRIES,
        block: suspend () -> ApolloResponse<D>,
    ): D =
        withContext(Dispatchers.IO) {
            if (!token.isNullOrBlank()) setAccessToken(token)
            executeWithRetry(tolerateErrors, retries, 0, block)
        }

    suspend fun <D : Operation.Data> executeOrNull(
        token: String? = null,
        block: suspend () -> ApolloResponse<D>,
    ): D? =
        try {
            execute(token, tolerateErrors = true, block = block)
        } catch (e: ApolloHttpException) {
            if (e.statusCode == 404) null else throw e
        }

    private tailrec suspend fun <D : Operation.Data> executeWithRetry(
        tolerateErrors: Boolean,
        retries: Int,
        attempt: Int,
        block: suspend () -> ApolloResponse<D>,
    ): D {
        val response = block()
        val data = response.data
        val errors = response.errors
        if (data != null && (tolerateErrors || errors.isNullOrEmpty())) return data
        val exception = response.exception
        if (isInvalidToken(exception, errors)) throw HttpException(401)
        val failure: Throwable =
            exception
                ?: errors?.firstOrNull()?.message?.let { IllegalStateException(it) }
                ?: IllegalStateException("Respons AniList kosong")
        val retryable = response.operation is Query<*> && attempt < retries && failure.isTransient()
        if (!retryable) throw failure
        (failure as? ApolloHttpException)?.body?.close()
        delay(RETRY_DELAY_MS * (attempt + 1))
        return executeWithRetry(tolerateErrors, retries, attempt + 1, block)
    }

    private fun isInvalidToken(
        exception: Throwable?,
        errors: List<Error>?,
    ): Boolean {
        if (errors?.any { it.message.contains(INVALID_TOKEN, ignoreCase = true) } == true) return true
        val http = exception as? ApolloHttpException ?: return false
        if (http.statusCode == 401) return true
        val body = runCatching { http.body?.readUtf8() }.getOrNull().orEmpty()
        return http.statusCode == 400 && body.contains(INVALID_TOKEN, ignoreCase = true)
    }

    private fun Throwable.isTransient(): Boolean =
        when (this) {
            is ApolloHttpException -> statusCode == 429 || statusCode in 500..599
            is ApolloNetworkException -> true
            else -> false
        }

    /**
     * Adds the Referer header to every AniList API request.
     * AniList currently requires this for third-party clients (403 without it).
     */
    private class RefererInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request =
                chain
                    .request()
                    .newBuilder()
                    .header("Referer", "$ANILIST_URL/")
                    .build()
            return chain.proceed(request)
        }
    }

    private class AuthorizationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = accessToken.get()
            val request =
                if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain
                        .request()
                        .newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
            return chain.proceed(request)
        }
    }
}
