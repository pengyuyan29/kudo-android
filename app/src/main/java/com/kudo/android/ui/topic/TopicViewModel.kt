package com.kudo.android.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class TopicState(val detail: TopicDetail? = null, val loading: Boolean = true, val loadingMore: Boolean = false, val error: Throwable? = null, val moreError: Throwable? = null, val busy: Set<Int> = emptySet())
class TopicViewModel(private val repository: CommunityRepository, val topicId: Int, private val number: Int? = null) : ViewModel() {
    private val _state = MutableStateFlow(TopicState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    init { refresh() }
    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null) }
        job = viewModelScope.launch {
            try { val detail = withContext(Dispatchers.IO) { repository.detail(topicId, number) }; _state.update { it.copy(detail = detail, loading = false) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(error = e, loading = false) } }
        }
    }
    fun more() {
        val snapshot = _state.value
        val detail = snapshot.detail ?: return
        if (snapshot.loadingMore || snapshot.loading) return
        val loaded = detail.posts.map { it.id }.toSet()
        val ids = detail.stream.filterNot { it in loaded }.take(20)
        if (ids.isEmpty()) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        job = viewModelScope.launch {
            try {
                val posts = withContext(Dispatchers.IO) { repository.posts(topicId, ids) }
                val available = posts.map { it.id }.toSet()
                _state.update { it.copy(detail = detail.copy(posts = (detail.posts + posts).distinctBy(Post::id).sortedBy(Post::number), stream = detail.stream.filter { id -> id !in ids || id in available }), loadingMore = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loadingMore = false, moreError = e) } }
        }
    }
    fun like(post: Post) = action(post) {
        repository.like(post)
        post.copy(liked = !post.liked, likes = (post.likes + if (post.liked) -1 else 1).coerceAtLeast(0))
    }
    fun bookmark(post: Post) = action(post) { post.copy(bookmarkId = repository.bookmark(post)) }
    private fun action(post: Post, block: suspend () -> Post) {
        if (post.id in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + post.id) }
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) { block() }
                _state.update { it.copy(detail = it.detail?.let { detail -> detail.copy(posts = detail.posts.map { old -> if (old.id == updated.id) updated else old }) }) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.send(e.message ?: "操作失败") }
            finally { _state.update { it.copy(busy = it.busy - post.id) } }
        }
    }
}
