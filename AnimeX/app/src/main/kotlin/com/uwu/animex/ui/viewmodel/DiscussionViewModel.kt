package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DISCUSSION_PAGE_SIZE = 30

enum class DiscussionScope {
    Movie,
    Episode,
}

/** Holds paging state outside the ViewModel so the loader lambda never captures `this` during super construction. */
private class DiscussionPager(
    private val targetId: String,
    private val scope: DiscussionScope,
) {
    val initialPage: Int = if (scope == DiscussionScope.Movie) 1 else 0
    var nextPage: Int = initialPage + 1
    var onReset: (() -> Unit)? = null
    var onFirstPage: ((Int) -> Unit)? = null

    suspend fun loadPage(page: Int): List<UserApi.Discussion> =
        when (scope) {
            DiscussionScope.Movie -> UserApi.discussionList(targetId, page)
            DiscussionScope.Episode -> UserApi.episodeCommentList(targetId, page)
        }

    suspend fun loadFirst(force: Boolean): List<UserApi.Discussion> {
        if (force) reset()
        return loadPage(initialPage).also { onFirstPage?.invoke(it.size) }
    }

    /** Re-fetches every page loaded so far, so a refresh keeps the "load more" progress. */
    suspend fun reloadLoadedPages(): List<UserApi.Discussion> {
        val pageCount = (nextPage - initialPage).coerceAtLeast(1)
        val seen = HashSet<String>()
        val merged = ArrayList<UserApi.Discussion>()
        for (i in 0 until pageCount) {
            for (item in loadPage(initialPage + i)) {
                if (item.id == null || seen.add(item.id)) merged += item
            }
        }
        return merged
    }

    fun reset() {
        nextPage = initialPage + 1
        onReset?.invoke()
    }
}

class DiscussionViewModel private constructor(
    private val targetId: String,
    private val scope: DiscussionScope,
    private val pager: DiscussionPager,
) : LoadViewModel<List<UserApi.Discussion>>(
        loader = { force -> pager.loadFirst(force) },
    ) {
    constructor(
        targetId: String,
        scope: DiscussionScope = DiscussionScope.Movie,
    ) : this(targetId, scope, DiscussionPager(targetId, scope))

    init {
        pager.onReset = {
            _hasMore.value = true
            _loadMoreError.value = false
        }
        pager.onFirstPage = { size -> _hasMore.value = size >= DISCUSSION_PAGE_SIZE }
    }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadMoreError = MutableStateFlow(false)
    val loadMoreError: StateFlow<Boolean> = _loadMoreError.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _sendError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendError: SharedFlow<String> = _sendError.asSharedFlow()

    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

    fun loadMore() {
        if (_loadingMore.value || !_hasMore.value) return
        val existingItems = (state.value as? UiState.Ready)?.value.orEmpty()
        if (existingItems.isEmpty()) {
            _hasMore.value = false
            return
        }
        val page = pager.nextPage
        _loadingMore.value = true
        _loadMoreError.value = false
        viewModelScope.launch {
            try {
                val result = pager.loadPage(page)
                val current = (state.value as? UiState.Ready)?.value.orEmpty()
                val knownIds = current.mapNotNull { it.id }.toHashSet()
                val newItems = result.filter { it.id == null || knownIds.add(it.id) }
                pager.nextPage = page + 1
                _hasMore.value = result.size >= DISCUSSION_PAGE_SIZE && newItems.isNotEmpty()
                updateReadyValue(current + newItems)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loadMoreError.value = true
                _sendError.tryEmit(e.toUserMessage())
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun send(
        text: String,
        onSent: () -> Unit,
    ) {
        if (_sending.value || _actionBusy.value) return
        _sending.value = true
        viewModelScope.launch {
            try {
                when (scope) {
                    DiscussionScope.Movie -> UserApi.discussionPost(targetId, text)
                    DiscussionScope.Episode -> UserApi.episodeCommentPost(targetId, text)
                }
                onSent()
                reloadLoadedPages()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.tryEmit(e.toUserMessage())
            } finally {
                _sending.value = false
            }
        }
    }

    fun react(discussion: UserApi.Discussion, type: String) {
        if (scope != DiscussionScope.Episode) return
        val id = discussion.id?.takeIf(String::isNotBlank) ?: return
        performAction(refreshAfter = true, onSuccess = {}) {
            UserApi.episodeCommentAction(id, type)
        }
    }

    fun loadReplies(discussion: UserApi.Discussion, onLoaded: (List<UserApi.Discussion>) -> Unit) {
        if (scope != DiscussionScope.Episode) return
        val id = discussion.id?.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch {
            try {
                onLoaded(UserApi.episodeCommentReplies(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.tryEmit(e.toUserMessage())
                onLoaded(emptyList())
            }
        }
    }

    fun reply(discussion: UserApi.Discussion, text: String, onSuccess: () -> Unit) {
        if (scope != DiscussionScope.Episode || text.isBlank()) return
        val id = discussion.id?.takeIf(String::isNotBlank) ?: return
        performAction(refreshAfter = true, onSuccess = onSuccess) {
            UserApi.episodeCommentReply(id, text.trim())
        }
    }

    fun edit(
        discussion: UserApi.Discussion,
        text: String,
        onSuccess: () -> Unit,
    ) {
        val id = discussion.id?.takeIf(String::isNotBlank) ?: return
        if (text.isBlank()) return
        performAction(refreshAfter = true, onSuccess = onSuccess) {
            when (scope) {
                DiscussionScope.Movie -> UserApi.discussionEdit(targetId, id, text)
                DiscussionScope.Episode -> UserApi.episodeCommentEdit(targetId, id, text)
            }
        }
    }

    fun delete(
        discussion: UserApi.Discussion,
        onSuccess: () -> Unit,
    ) {
        val id = discussion.id?.takeIf(String::isNotBlank) ?: return
        performAction(refreshAfter = true, onSuccess = onSuccess) {
            when (scope) {
                DiscussionScope.Movie -> UserApi.discussionDelete(id)
                DiscussionScope.Episode -> UserApi.episodeCommentDelete(id)
            }
        }
    }

    fun report(
        discussion: UserApi.Discussion,
        onSuccess: () -> Unit,
    ) {
        val id = discussion.id?.takeIf(String::isNotBlank) ?: return
        performAction(refreshAfter = false, onSuccess = onSuccess) {
            when (scope) {
                DiscussionScope.Movie -> UserApi.discussionReport(id)
                DiscussionScope.Episode -> UserApi.episodeCommentReport(id)
            }
        }
    }

    fun retryLoadMore() {
        _loadMoreError.value = false
        loadMore()
    }

    private fun reloadLoadedPages() = reloadWith { pager.reloadLoadedPages() }

    private fun performAction(
        refreshAfter: Boolean,
        onSuccess: () -> Unit,
        action: suspend () -> Unit,
    ) {
        if (_sending.value || _actionBusy.value) return
        _actionBusy.value = true
        viewModelScope.launch {
            try {
                action()
                if (refreshAfter) reloadLoadedPages()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.tryEmit(e.toUserMessage())
            } finally {
                _actionBusy.value = false
            }
        }
    }

    companion object {
        fun factory(
            targetId: String,
            scope: DiscussionScope = DiscussionScope.Movie,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DiscussionViewModel(targetId, scope)
                }
            }
    }
}
