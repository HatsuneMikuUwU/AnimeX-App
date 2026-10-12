package com.uwu.animex.core.cache

import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.ApiCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MemoryPolicy
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.Validator
import kotlin.time.Duration

data class ApiKey(
    val path: String,
    val params: Map<String, String>,
) {
    val id: String get() = path + params.toSortedMap().toString()
}

class ApiEntry(
    val body: String,
    val savedAt: Long,
    val ttlMs: Long,
) {
    fun isFresh(): Boolean = System.currentTimeMillis() - savedAt <= ttlMs
}

suspend fun <K : Any, V : Any> Store<K, V>.read(
    key: K,
    force: Boolean = false,
): V {
    val request = if (force) StoreReadRequest.fresh(key) else StoreReadRequest.cached(key, refresh = false)
    val response = stream(request).first { it is StoreReadResponse.Data || it is StoreReadResponse.Error }
    response.throwIfError()
    return response.requireData()
}

object ApiStores {
    fun disk(
        dao: AnimeDao,
        ttlFor: (String) -> Long,
        load: suspend (ApiKey) -> ApiEntry,
    ): Store<ApiKey, ApiEntry> {
        val fetcher = Fetcher.of { key: ApiKey -> load(key) }

        val sourceOfTruth: SourceOfTruth<ApiKey, ApiEntry, ApiEntry> =
            SourceOfTruth.of(
                reader = { key ->
                    flow {
                        val saved = dao.getApiCache(key.id)
                        emit(saved?.let { ApiEntry(it.body, it.savedAt, ttlFor(key.path)) })
                    }
                },
                writer = { key, entry ->
                    dao.upsertApiCache(ApiCacheEntity(key.id, entry.body, entry.savedAt))
                },
            )

        return StoreBuilder
            .from(fetcher, sourceOfTruth)
            .validator(Validator.by { it.isFresh() })
            .build()
    }

    fun <K : Any, V : Any> memory(
        maxEntries: Long,
        ttl: Duration,
        load: suspend (K) -> V,
    ): Store<K, V> =
        StoreBuilder
            .from(Fetcher.of { key: K -> load(key) })
            .cachePolicy(MemoryPolicy.builder<K, V>().setMaxSize(maxEntries).setExpireAfterWrite(ttl).build())
            .build()
}
