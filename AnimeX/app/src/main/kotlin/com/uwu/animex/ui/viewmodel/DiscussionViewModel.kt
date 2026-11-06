package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.user.UserApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DiscussionScope {
    Movie,
    Episode,
}

class DiscussionViewModel(
    private val targetId: String,
    private val scope: DiscussionScope = DiscussionScope.Movie,
) : LoadViewModel<List<UserApi.Discussion>>(
        loader = {
            when (scope) {
                DiscussionScope.Movie -> UserApi.discussionList(targetId)
                DiscussionScope.Episode -> UserApi.episodeCommentList(targetId)
            }
        },
    ) {
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _sendError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendError: SharedFlow<String> = _sendError.asSharedFlow()

    private val _actionBusy = MutableStateFlow(false)
    val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

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
                refresh(force = true, showIndicator = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _sendError.tryEmit(e.toUserMessage())
            } finally {
                _sending.value = false
            }
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
                if (refreshAfter) refresh(force = true, showIndicator = false)
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
