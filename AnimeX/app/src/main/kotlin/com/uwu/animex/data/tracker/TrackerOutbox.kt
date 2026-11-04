package com.uwu.animex.data.tracker

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.TrackerDao
import com.uwu.animex.data.local.db.TrackerMetaEntity
import com.uwu.animex.data.local.db.TrackerPendingOpEntity
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.anilist.AniListApi
import com.uwu.animex.sync.anilist.AniListEditUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SubmitResult {

    Sent,

    Rejected,

    Queued,
}

data class FlushReport(
    val retry: Boolean,
    val sent: Int,
    val dropped: Int,
)

object TrackerOutbox {
    private val gson = Gson()
    private val locks = TrackerSource.entries.associateWith { Mutex() }

    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun init(context: Context) {
        attach(context)
        AppScope.io.launch {
            if (dao().pendingCount() > 0) TrackerSyncScheduler.schedule(requireContext())
        }
    }

    fun pendingCount(source: TrackerSource): Flow<Int> = dao().observePendingCount(source.key)

    suspend fun submitStatus(
        source: TrackerSource,
        id: String,
        status: SyncStatus,
    ): SubmitResult =
        submit(source, id, KIND_STATUS, gson.toJson(status)) {
            source.repo.updateStatus(id, status).getOrThrow()
        }

    suspend fun submitAniListEntry(
        id: Int,
        update: AniListEditUpdate,
    ): SubmitResult =
        submit(TrackerSource.ANILIST, id.toString(), KIND_ANILIST_ENTRY, gson.toJson(update)) {
            saveAniListEntry(id, update)
        }

    suspend fun submitDelete(
        source: TrackerSource,
        id: String,
    ): SubmitResult =
        submit(source, id, KIND_DELETE, null) {
            source.repo.removeStatus(id).getOrThrow()
        }

    private suspend fun saveAniListEntry(
        id: Int,
        update: AniListEditUpdate,
    ): Boolean {
        val repo = TrackerSource.ANILIST.repo
        return repo.withAuth { (repo.api as AniListApi).saveEntry(it, id, update) }
    }

    private suspend fun submit(
        source: TrackerSource,
        syncId: String,
        kind: String,
        payload: String?,
        sendNow: suspend () -> Boolean,
    ): SubmitResult {
        val result =
            locks.getValue(source).withLock {
                val hasPending = dao().countOpsFor(source.key, syncId) > 0

                if (hasPending || !ConnectivityMonitor.isOnline()) {
                    enqueue(source, syncId, kind, payload)
                    return@withLock SubmitResult.Queued
                }
                try {
                    if (sendNow()) SubmitResult.Sent else SubmitResult.Rejected
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!e.isTransientSyncFailure()) throw e
                    enqueue(source, syncId, kind, payload)
                    SubmitResult.Queued
                }
            }
        if (result == SubmitResult.Queued) TrackerSyncScheduler.schedule(requireContext())
        return result
    }

    private suspend fun enqueue(
        source: TrackerSource,
        syncId: String,
        kind: String,
        payload: String?,
    ) {
        val db = db()
        val dao = db.trackerDao()
        db.withTransaction {
            val now = System.currentTimeMillis()
            val last = if (kind == KIND_DELETE) null else dao.lastOpFor(source.key, syncId)
            if (kind == KIND_DELETE) {

                dao.deleteOpsFor(source.key, syncId)
            }
            if (last != null && last.kind == kind) {
                dao.updateOp(last.copy(payload = mergePayload(kind, last.payload, payload), createdAt = now, attempts = 0))
            } else {
                dao.insertOp(
                    TrackerPendingOpEntity(
                        source = source.key,
                        syncId = syncId,
                        kind = kind,
                        payload = payload,
                        createdAt = now,
                    ),
                )
            }
            Unit
        }
    }

    private fun mergePayload(
        kind: String,
        old: String?,
        new: String?,
    ): String? {

        if (kind != KIND_STATUS) return new
        val newer = gson.fromJson(new, SyncStatus::class.java)
        val older = runCatching { gson.fromJson(old, SyncStatus::class.java) }.getOrNull()
        return gson.toJson(older?.mergedWith(newer) ?: newer)
    }

    suspend fun flush(source: TrackerSource): FlushReport {
        val dao = dao()
        if (source.repo.authUser() == null) {
            dao.deleteOps(source.key)
            return FlushReport(retry = false, sent = 0, dropped = 0)
        }
        var sent = 0
        var dropped = 0
        while (true) {
            val step = locks.getValue(source).withLock { flushNext(source, dao) }
            when (step) {
                Step.Empty -> break
                Step.Sent -> sent++
                Step.Dropped -> dropped++
                Step.Retry -> return FlushReport(retry = true, sent = sent, dropped = dropped)
            }
        }
        if (dropped > 0) markStale(source, dao)
        return FlushReport(retry = false, sent = sent, dropped = dropped)
    }

    private enum class Step { Empty, Sent, Dropped, Retry }

    private suspend fun flushNext(
        source: TrackerSource,
        dao: TrackerDao,
    ): Step {
        val op = dao.firstOp(source.key) ?: return Step.Empty
        return try {
            val ok = execute(source, op)
            dao.deleteOp(op.id)
            if (ok) Step.Sent else Step.Dropped
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                source.repo.authUser() == null -> {

                    dao.deleteOps(source.key)
                    Step.Empty
                }
                op.kind == KIND_DELETE && e.httpStatus() == 404 -> {
                    dao.deleteOp(op.id)
                    Step.Sent
                }
                e.isTransientSyncFailure() && op.attempts + 1 < MAX_OP_ATTEMPTS -> {
                    dao.bumpAttempts(op.id)
                    Step.Retry
                }
                else -> {
                    dao.deleteOp(op.id)
                    Step.Dropped
                }
            }
        }
    }

    private suspend fun execute(
        source: TrackerSource,
        op: TrackerPendingOpEntity,
    ): Boolean {
        val repo = source.repo
        return when (op.kind) {
            KIND_STATUS -> repo.updateStatus(op.syncId, gson.fromJson(op.payload, SyncStatus::class.java)).getOrThrow()
            KIND_ANILIST_ENTRY -> {
                val id = op.syncId.toIntOrNull() ?: return false
                saveAniListEntry(id, gson.fromJson(op.payload, AniListEditUpdate::class.java))
            }
            KIND_DELETE -> repo.removeStatus(op.syncId).getOrThrow()
            else -> false
        }
    }

    private suspend fun markStale(
        source: TrackerSource,
        dao: TrackerDao,
    ) {
        dao.upsertMeta(TrackerMetaEntity(source.key, 0L))
        source.repo.requireLibraryRefresh = true
    }

    private fun requireContext(): Context = appContext ?: error("TrackerOutbox belum di-init")

    private fun db() = AnimeDatabase.get(requireContext())

    private fun dao() = db().trackerDao()
}
