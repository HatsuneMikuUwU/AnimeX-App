package com.uwu.animex.ui.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.History
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Satu-satunya pintu UI ke fitur unduhan (daftar, kontrol per-item, folder tujuan). */
class DownloadsViewModel(private val appContext: Context) : ViewModel() {

    val items: StateFlow<Map<String, Downloads.Item>> = Downloads.items
    val folderUri: StateFlow<String?> = Downloads.folderUri

    fun itemFlow(episodeId: String?): Flow<Downloads.Item?> = Downloads.itemFlow(episodeId)

    fun item(episodeId: String?): Downloads.Item? = Downloads.item(episodeId)

    fun completedUrl(episodeId: String?): String? = Downloads.completedUrl(episodeId)

    fun enqueue(episodeId: String, url: String, meta: Downloads.Meta) =
        Downloads.enqueue(appContext, episodeId, url, meta)

    fun pause(id: String) = Downloads.pause(appContext, id)

    fun resume(id: String) = Downloads.resume(appContext, id)

    fun retry(id: String) = Downloads.retry(appContext, id)

    fun remove(id: String) = Downloads.remove(appContext, id)

    fun setFolder(uri: Uri) = Downloads.setFolder(appContext, uri)

    /** Dipanggil saat user memutar episode hasil unduhan, supaya muncul di "Lanjut Nonton". */
    fun recordPlayed(movie: Movie, episodeIndex: String?, episodeId: String) =
        History.record(movie, episodeIndex, episodeId)
}
