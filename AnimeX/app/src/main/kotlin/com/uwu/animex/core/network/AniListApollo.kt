package com.uwu.animex.core.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
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
