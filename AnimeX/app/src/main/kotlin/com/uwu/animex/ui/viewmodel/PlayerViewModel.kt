package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    initialEpId: String,
) : ViewModel() {
    private val _servers = MutableStateFlow<UiState<List<Server>>>(UiState.Loading)
    val servers: StateFlow<UiState<List<Server>>> = _servers.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _episodes = MutableStateFlow<UiState<List<Episode>>>(UiState.Loading)
    val episodes: StateFlow<UiState<List<Episode>>> = _episodes.asStateFlow()

    private var serversJob: Job? = null
    private var episodesJob: Job? = null
    private var currentEpId: String = initialEpId
    private var serversGeneration = 0

    init {
        loadServers(initialEpId, force = false)
    }

    fun loadServers(
        epId: String,
        force: Boolean = false,
    ) {
        currentEpId = epId
        serversGeneration++
        val gen = serversGeneration
        serversJob?.cancel()
        serversJob =
            viewModelScope.launch {
                val previous = _servers.value
                if (force && previous is UiState.Ready) {
                    _isRefreshing.value = true
                } else if (previous !is UiState.Ready || force) {
                    _servers.value = UiState.Loading
                }
                try {
                    val offlineUrl = Downloads.completedUrl(epId)
                    val list =
                        if (offlineUrl != null) {
                            listOf(
                                Server(
                                    id = epId,
                                    link = offlineUrl,
                                    quality = Downloads.item(epId)?.meta?.quality ?: "Offline",
                                    type = "direct",
                                ),
                            )
                        } else {
                            Api.servers(epId, force = force).sortedWith(
                                compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue },
                            )
                        }
                    if (gen == serversGeneration) {
                        _servers.value = UiState.Ready(list)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (gen == serversGeneration) {
                        _servers.value =
                            when {
                                previous is UiState.Ready -> previous
                                else -> UiState.Error(e.toUserMessage())
                            }
                    }
                } finally {
                    if (gen == serversGeneration) {
                        _isRefreshing.value = false
                    }
                }
            }
    }

    fun refreshServers() = loadServers(currentEpId, force = true)

    fun loadEpisodes(
        movieId: String,
        force: Boolean = false,
    ) {
        episodesJob?.cancel()
        episodesJob =
            viewModelScope.launch {
                if (_episodes.value !is UiState.Ready) {
                    _episodes.value = UiState.Loading
                }
                try {
                    val all = LinkedHashMap<String, Episode>()

                    fun add(list: List<Episode>) = list.forEach { e -> e.id?.let { all.putIfAbsent(it, e) } }
                    add(Api.episodes(movieId, force = force))
                    var page = 1
                    while (page <= MAX_EPISODE_PAGES) {
                        val batch =
                            try {
                                Api.episodes(movieId, page = page, force = force)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                break
                            }
                        if (batch.isEmpty()) break
                        add(batch)
                        page++
                    }
                    _episodes.value =
                        UiState.Ready(all.values.sortedBy { it.index?.toIntOrNull() ?: Int.MAX_VALUE })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (_episodes.value !is UiState.Ready) {
                        _episodes.value = UiState.Error(e.toUserMessage())
                    }
                }
            }
    }

    companion object {
        private const val MAX_EPISODE_PAGES = 40

        fun factory(epId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(epId)
                }
            }
    }
}
