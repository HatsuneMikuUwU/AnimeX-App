package com.uwu.animex.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val tlsOnly = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionSpecs(tlsOnly)
            .dns(DohDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    private fun dispatcher(
        maxRequests: Int,
        perHost: Int,
    ) = Dispatcher().apply {
        this.maxRequests = maxRequests
        this.maxRequestsPerHost = perHost
    }

    val apiClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .dispatcher(dispatcher(maxRequests = 32, perHost = 12))
            .connectTimeout(20, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                val uid = com.uwu.animex.data.animein.AnimeIn.userId.value
                val key = com.uwu.animex.data.animein.AnimeIn.keyClient.value

                var currentReq = req.newBuilder().header("User-Agent", "okhttp/4.12.0").build()

                if (!uid.isNullOrBlank() && !key.isNullOrBlank() && (host.contains("animein") || host.contains("nextanimelist"))) {
                    if (currentReq.url.queryParameter("id_user") == null) {
                        if (currentReq.method == "GET" || currentReq.method == "DELETE") {
                            val newUrl =
                                currentReq.url
                                    .newBuilder()
                                    .addQueryParameter("id_user", uid)
                                    .addQueryParameter("key_client", key)
                                    .addQueryParameter("apk_ver", com.uwu.animex.data.animein.AnimeIn.APK_VER)
                                    .build()
                            currentReq = currentReq.newBuilder().url(newUrl).build()
                        } else if (currentReq.body is okhttp3.FormBody) {
                            val formBody = currentReq.body as okhttp3.FormBody
                            var hasId = false
                            for (i in 0 until formBody.size) {
                                if (formBody.name(i) == "id_user") {
                                    hasId = true
                                    break
                                }
                            }
                            if (!hasId) {
                                val formBuilder = okhttp3.FormBody.Builder()
                                for (i in 0 until formBody.size) {
                                    formBuilder.addEncoded(formBody.encodedName(i), formBody.encodedValue(i))
                                }
                                formBuilder.add("id_user", uid)
                                formBuilder.add("key_client", key)
                                formBuilder.add("apk_ver", com.uwu.animex.data.animein.AnimeIn.APK_VER)
                                currentReq = currentReq.newBuilder().method(currentReq.method, formBuilder.build()).build()
                            }
                        }
                    }
                }
                chain.proceed(currentReq)
            }.build()
    }

    val streamClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private const val IMAGE_MAX_AGE_S = 30L * 24 * 60 * 60

    fun evictConnectionsForDnsChange() {
        maintenanceScope.launch { client.connectionPool.evictAll() }
    }

    val imageClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .dispatcher(dispatcher(maxRequests = 64, perHost = 16))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply {
                interceptors().removeAll { it is RetryInterceptor }
                addInterceptor(RetryInterceptor(maxRetries = 1, baseDelayMs = 300L, maxRetryAfterMs = 3_000L))
            }.addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (!response.isSuccessful) {
                    response
                } else {
                    response
                        .newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .header("Cache-Control", "public, max-age=$IMAGE_MAX_AGE_S")
                        .build()
                }
            }.build()
    }
}
