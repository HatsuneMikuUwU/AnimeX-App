package com.uwu.animex.core.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.Validator

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

object ApiStores {
    fun create(
        disk: ResponseCache,
        ttlFor: (String) -> Long,
        load: suspend (ApiKey) -> ApiEntry,
    ): Store<ApiKey, ApiEntry> {
        val fetcher = Fetcher.of { key: ApiKey -> load(key) }

        val sourceOfTruth: SourceOfTruth<ApiKey, ApiEntry, ApiEntry> =
            SourceOfTruth.of(
                reader = { key ->
                    flow {
                        val saved = withContext(Dispatchers.IO) { disk.get(key.id) }
                        emit(saved?.let { ApiEntry(it.body, it.savedAt, ttlFor(key.path)) })
                    }
                },
                writer = { key, entry ->
                    withContext(Dispatchers.IO) { disk.put(key.id, ResponseCache.Entry(entry.body, entry.savedAt)) }
                },
            )

        return StoreBuilder
            .from(fetcher, sourceOfTruth)
            .validator(Validator.by { it.isFresh() })
            .build()
    }
}
