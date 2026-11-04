package com.uwu.animex.data.tracker

import android.content.Context
import androidx.room.withTransaction
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.anilist.LibraryRefreshVersion
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.TrackerDao
import com.uwu.animex.data.local.db.TrackerMetaEntity
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.LibraryMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LocalMutation { Updated, Created, Missing }

internal class TrackerLibraryCache(
    private val source: TrackerSource,
    private val failureMessage: String,
    private val onMetadata: (LibraryMetadata) -> Unit = {},
) {
    private val refreshLock = Mutex()
    private val writeLock = Mutex()
    private val version = LibraryRefreshVersion()
    private val hydrated = CompletableDeferred<Unit>()

    private var db: AnimeDatabase? = null
    private var dao: TrackerDao? = null

    @Volatile
    private var started = false

    @Volatile
    private var lastRefresh = 0L

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val loaded: Boolean get() = hydrated.isCompleted && (lastRefresh > 0L || _items.value.isNotEmpty())

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        val database = AnimeDatabase.get(context.applicationContext)
        val trackerDao = database.trackerDao()
        db = database
        dao = trackerDao
        AppScope.io.launch {
            lastRefresh = trackerDao.lastRefreshAt(source.key) ?: 0L
            if (lastRefresh == 0L) source.repo.requireLibraryRefresh = true
            trackerDao
                .observeEntries(source.key)
                .map { rows -> rows.map { it.toLibraryItem() } }
                .flowOn(Dispatchers.Default)
                .collect {
                    _items.value = it
                    hydrated.complete(Unit)
                }
        }
    }

    fun clear() {
        version.invalidateAnd {
            _items.value = emptyList()
            lastRefresh = 0L
            source.repo.requireLibraryRefresh = true
        }
        val database = db ?: return
        val trackerDao = dao ?: return
        AppScope.io.launch {
            writeLock.withLock {
                database.withTransaction {
                    trackerDao.deleteEntries(source.key)
                    trackerDao.deleteOps(source.key)
                    trackerDao.deleteMeta(source.key)
                }
            }
        }
    }

    suspend fun refresh(force: Boolean) {
        val repo = source.repo
        if (repo.authUser() == null) return
        val database = db ?: return
        val trackerDao = dao ?: return
        hydrated.await()

        val fresh = !isLibraryStale(lastRefresh)
        if (!force && fresh && !repo.requireLibraryRefresh) return

        if (!force && lastRefresh > 0L && !ConnectivityMonitor.isOnline()) return
        if (!refreshLock.tryLock()) return
        try {
            _refreshing.value = true
            _error.value = null
            var attempt = 0
            while (true) {
                val startVersion = version.capture()
                val meta = repo.library().getOrThrow() ?: throw IllegalStateException(failureMessage)
                val rows = meta.allLibraryLists.flatMap { it.items }.map { it.toEntity(source.key) }
                val now = System.currentTimeMillis()

                val committed =
                    writeLock.withLock {
                        if (!version.isCurrent(startVersion)) {
                            false
                        } else {
                            database.withTransaction {
                                val pending = trackerDao.pendingSyncIds(source.key).toHashSet()
                                trackerDao.deleteEntriesWithoutPendingOps(source.key)
                                trackerDao.upsertEntries(
                                    if (pending.isEmpty()) {
                                        rows
                                    } else {
                                        rows.filter {
                                            it.syncId !in
                                                pending
                                        }
                                    },
                                )
                                trackerDao.upsertMeta(TrackerMetaEntity(source.key, now))
                            }
                            lastRefresh = now
                            true
                        }
                    }
                if (committed) {
                    onMetadata(meta)
                    break
                }
                if (attempt++ >= 2) return
            }
            repo.requireLibraryRefresh = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _refreshing.value = false
            refreshLock.unlock()
        }
    }

    suspend fun mutate(
        syncId: String,
        update: (LibraryItem) -> LibraryItem,
        create: (() -> LibraryItem)?,
    ): LocalMutation {
        val trackerDao = dao ?: return LocalMutation.Missing
        return writeLock.withLock {
            version.invalidate()
            val existing = trackerDao.getEntry(source.key, syncId)
            when {
                existing != null -> {
                    trackerDao.upsertEntry(update(existing.toLibraryItem()).toEntity(source.key))
                    LocalMutation.Updated
                }
                create != null -> {
                    trackerDao.upsertEntry(create().toEntity(source.key))
                    LocalMutation.Created
                }
                else -> LocalMutation.Missing
            }
        }
    }

    suspend fun remove(syncId: String) {
        val trackerDao = dao ?: return
        writeLock.withLock {
            version.invalidate()
            trackerDao.deleteEntry(source.key, syncId)
        }
    }
}
