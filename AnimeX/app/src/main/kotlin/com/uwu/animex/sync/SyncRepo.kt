package com.uwu.animex.sync

class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val mainUrl: String get() = api.mainUrl
    val supportedWatchTypes: Set<SyncWatchType> get() = api.supportedWatchTypes

    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    suspend fun updateStatus(id: String, newStatus: SyncStatus): Result<Boolean> = runCatching {
        withAuth { api.updateStatus(it, id, newStatus) }
    }

    suspend fun removeStatus(id: String): Result<Boolean> = runCatching {
        withAuth { api.removeStatus(it, id) }
    }

    suspend fun status(id: String): Result<SyncStatus?> = runCatching {
        withAuth { api.status(it, id) }
    }

    suspend fun load(id: String): Result<SyncResult?> = runCatching {
        // Prefer user token when logged in; otherwise public client-id (mean score, etc.).
        val auth = runCatching { freshAuth() }.getOrNull()
        api.load(auth, id)
    }

    suspend fun search(query: String): Result<List<SyncSearchResult>?> = runCatching {
        val auth = runCatching { freshAuth() }.getOrNull()
        api.search(auth, query)
    }

    suspend fun library(): Result<LibraryMetadata?> = runCatching {
        withAuth { api.library(it) }
    }
}
