package com.uwu.animex.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.uwu.animex.data.db.AnimeDao
import com.uwu.animex.data.db.AnimeDatabase
import com.uwu.animex.data.remote.ConnectivityObserver
import com.uwu.animex.data.remote.NetworkClient
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.BookmarkRepository
import com.uwu.animex.data.repository.HistoryRepository
import com.uwu.animex.data.repository.ProgressRepository
import com.uwu.animex.data.repository.SearchHistoryRepository
import okio.Path.Companion.toOkioPath

/**
 * Manual dependency container. Replaces scattered object.init(Context) singletons.
 * Initialized once from Application / MainActivity.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }

    val networkClient: NetworkClient by lazy {
        NetworkClient(appContext).also { client ->
            // Initial online state
            client.setNetworkAvailable(connectivityObserver.isOnline)
        }
    }

    val database: AnimeDatabase by lazy { AnimeDatabase.get(appContext) }

    val dao: AnimeDao by lazy { database.animeDao() }

    val animeRepository: AnimeRepository by lazy {
        AnimeRepository(networkClient)
    }

    val bookmarkRepository: BookmarkRepository by lazy {
        BookmarkRepository(dao)
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(dao)
    }

    val progressRepository: ProgressRepository by lazy {
        ProgressRepository(dao)
    }

    val searchHistoryRepository: SearchHistoryRepository by lazy {
        SearchHistoryRepository(dao)
    }

    /** Optimized Coil ImageLoader: memory + disk cache, OkHttp-backed. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { networkClient.client }))
            }
            .memoryCache {
                // ~25% of typical mid-range heap, clamp to 15–40 MB
                val maxBytes = (Runtime.getRuntime().maxMemory() * 0.25).toLong()
                    .coerceIn(15L * 1024 * 1024, 40L * 1024 * 1024)
                MemoryCache.Builder()
                    .maxSizeBytes(maxBytes)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }

        /** For tests / process death recovery. */
        fun reset() {
            instance = null
        }
    }
}
