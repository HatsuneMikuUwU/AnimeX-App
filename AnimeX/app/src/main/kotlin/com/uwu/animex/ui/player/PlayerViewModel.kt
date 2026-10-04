package com.uwu.animex.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.AniSkip
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.Episode
import com.uwu.animex.data.Mal
import com.uwu.animex.data.Server
import com.uwu.animex.data.SkipStamp
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Server, daftar episode, dan episode berikutnya untuk layar Player. */
class PlayerViewModel(private val animeRepo: AnimeRepository) : ViewModel() {

    // ---- server ----
    private val _servers = MutableStateFlow<UiState<List<Server>>>(UiState.Loading)
    val servers: StateFlow<UiState<List<Server>>> = _servers.asStateFlow()
    private var serversJob: Job? = null

    fun loadServers(epId: String, force: Boolean = false) {
        serversJob?.cancel()
        serversJob = viewModelScope.launch {
            _servers.value = UiState.Loading
            try {
                val offlineUrl = Downloads.completedUrl(epId)
                val list = if (offlineUrl != null) {
                    listOf(
                        Server(
                            id = epId,
                            link = offlineUrl,
                            quality = Downloads.item(epId)?.meta?.quality ?: "Offline",
                            type = "direct",
                        ),
                    )
                } else {
                    animeRepo.servers(epId, force = force).sortedWith(
                        compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue },
                    )
                }
                _servers.value = UiState.Ready(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _servers.value = UiState.Error(e.message ?: "Gagal memuat. Cek koneksi internet.")
            }
        }
    }

    // ---- daftar episode (panel) ----
    private val _episodes = MutableStateFlow<UiState<List<Episode>>>(UiState.Loading)
    val episodes: StateFlow<UiState<List<Episode>>> = _episodes.asStateFlow()
    private var episodesFor: String? = null

    fun loadEpisodes(movieId: String, force: Boolean = false) {
        if (!force && episodesFor == movieId && _episodes.value is UiState.Ready) return
        episodesFor = movieId
        viewModelScope.launch {
            if (_episodes.value !is UiState.Ready) _episodes.value = UiState.Loading
            try {
                _episodes.value = UiState.Ready(loadAllEpisodes(movieId, force))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_episodes.value !is UiState.Ready) {
                    _episodes.value = UiState.Error(e.message ?: "Gagal memuat. Cek koneksi internet.")
                }
            }
        }
    }

    private suspend fun loadAllEpisodes(movieId: String, force: Boolean): List<Episode> {
        val all = LinkedHashMap<String, Episode>()
        fun add(list: List<Episode>) = list.forEach { e -> e.id?.let { all.putIfAbsent(it, e) } }
        add(animeRepo.episodes(movieId, force = force))
        var page = 1
        while (page <= 40) {
            val batch = runCatching { animeRepo.episodes(movieId, page = page, force = force) }.getOrNull().orEmpty()
            if (batch.isEmpty()) break
            add(batch)
            page++
        }
        return all.values.sortedBy { it.index?.toIntOrNull() ?: Int.MAX_VALUE }
    }

    // ---- episode berikutnya ----
    private val _nextLookup = MutableStateFlow<AnimeRepository.NextEpisodeLookup>(AnimeRepository.NextEpisodeLookup.Unknown)

    /** Hasil lookup katalog (tidak butuh server) — dipakai untuk "Lanjut Nonton". */
    val nextLookup: StateFlow<AnimeRepository.NextEpisodeLookup> = _nextLookup.asStateFlow()

    private val _nextEp = MutableStateFlow<Episode?>(null)

    /** Episode berikutnya yang bisa diputar (untuk auto-next); bisa null walau katalog punya. */
    val nextEp: StateFlow<Episode?> = _nextEp.asStateFlow()

    suspend fun resolveNext(movieId: String?, index: String?) {
        _nextEp.value = null
        _nextLookup.value = AnimeRepository.NextEpisodeLookup.Unknown
        if (movieId == null || index == null) return
        val lookup = runCatching { animeRepo.lookupNextEpisode(movieId, index, requireServers = false) }
            .getOrDefault(AnimeRepository.NextEpisodeLookup.Unknown)
        _nextLookup.value = lookup
        _nextEp.value = runCatching { animeRepo.nextEpisode(movieId, index) }.getOrNull()
            ?: (lookup as? AnimeRepository.NextEpisodeLookup.Exists)?.episode
    }

    // ---- skip intro/outro ----
    suspend fun skipStamps(movieId: String?, episodeIndex: String?, durationMs: Long): List<SkipStamp> {
        val malId = Mal.malIdFor(movieId)
        val ep = episodeIndex?.toIntOrNull()
        return if (malId != null && ep != null) AniSkip.stamps(malId, ep, durationMs) else emptyList()
    }
}
