package com.uwu.animex.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bypass Cloudflare challenge ("Just a moment...") lewat WebView tersembunyi.
 *
 * Alur:
 *  1. Request ke host target dikirim dengan cookie WebView (cf_clearance) + UA yang sama.
 *  2. Kalau respons-nya challenge Cloudflare (403/503 + server cloudflare / cf-mitigated),
 *     WebView di-load ke URL itu, tunggu sampai cf_clearance muncul, lalu request di-retry.
 *
 * cf_clearance terikat ke User-Agent, jadi UA WebView dipaksa sama dengan UA OkHttp.
 */
class CloudflareKiller(
    private val hostMatcher: (String) -> Boolean,
    private val userAgent: String,
    private val timeoutMs: Long = 35_000L,
) : Interceptor {
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!hostMatcher(original.url.host)) return chain.proceed(original)

        val withCookies = original.withClearance()
        val first = chain.proceed(withCookies)
        if (!first.isCloudflareChallenge()) return first

        val sentCookie = withCookies.header("Cookie")
        first.close()

        synchronized(lock) {
            // Thread lain mungkin baru selesai solve; cek dulu cookie sudah berubah belum.
            val current = original.withClearance()
            val refreshed = current.header("Cookie") != sentCookie && current.hasClearance()
            if (!refreshed) {
                clearStaleClearance(original)
                if (!solve(original.url.toString())) {
                    throw IOException("Gagal melewati proteksi Cloudflare")
                }
            }
        }

        val retry = original.withClearance()
        val second = chain.proceed(retry)
        if (second.isCloudflareChallenge()) {
            second.close()
            throw IOException("Cloudflare masih memblokir request")
        }
        return second
    }

    private fun Request.withClearance(): Request {
        val b = newBuilder().header("User-Agent", userAgent)
        val cookies = runCatching { CookieManager.getInstance().getCookie(url.toString()) }.getOrNull()
        if (!cookies.isNullOrBlank()) {
            val existing = header("Cookie")
            b.header("Cookie", if (existing.isNullOrBlank()) cookies else "$existing; $cookies")
        }
        return b.build()
    }

    private fun Request.hasClearance(): Boolean = header("Cookie")?.contains("cf_clearance") == true

    private fun Response.isCloudflareChallenge(): Boolean {
        if (code != 403 && code != 503) return false
        if (header("cf-mitigated").equals("challenge", ignoreCase = true)) return true
        return header("Server")?.startsWith("cloudflare", ignoreCase = true) == true
    }

    private fun clearStaleClearance(request: Request) {
        runCatching {
            val cm = CookieManager.getInstance()
            val url = request.url.toString()
            cm.getCookie(url)
                ?.split(";")
                ?.map { it.substringBefore("=").trim() }
                ?.filter { it == "cf_clearance" }
                ?.forEach { name ->
                    cm.setCookie(url, "$name=; Max-Age=0; Path=/")
                }
            cm.flush()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun solve(url: String): Boolean {
        val ctx = appContext ?: return false
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        val deadline = System.currentTimeMillis() + timeoutMs

        lateinit var poll: Runnable
        poll =
            Runnable {
                val wv = webView ?: return@Runnable
                val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()
                val title = wv.title.orEmpty()
                val solved = cookies.contains("cf_clearance") && !title.contains("just a moment", true)
                when {
                    solved -> {
                        ok.set(true)
                        latch.countDown()
                    }
                    System.currentTimeMillis() > deadline -> latch.countDown()
                    else -> main.postDelayed(poll, 500L)
                }
            }

        main.post {
            try {
                val wv = WebView(ctx)
                webView = wv
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(wv, true)
                }
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = userAgent
                    cacheMode = WebSettings.LOAD_DEFAULT
                    blockNetworkImage = true
                }
                wv.webViewClient = WebViewClient()
                wv.loadUrl(url)
                main.postDelayed(poll, 1_000L)
            } catch (_: Throwable) {
                latch.countDown()
            }
        }

        try {
            latch.await(timeoutMs + 2_000L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            main.removeCallbacks(poll)
            main.post {
                runCatching {
                    CookieManager.getInstance().flush()
                    webView?.stopLoading()
                    webView?.destroy()
                }
                webView = null
            }
        }
        return ok.get()
    }

    companion object {
        @Volatile
        private var appContext: Context? = null

        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}
