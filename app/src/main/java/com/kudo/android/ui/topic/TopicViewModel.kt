package com.kudo.android.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class TopicState(
    val detail: TopicDetail? = null, val loading: Boolean = true, val loadingMore: Boolean = false,
    val error: Throwable? = null, val moreError: Throwable? = null, val busy: Set<Int> = emptySet(),
    val viewerId: Int? = null, val authorProfile: Member? = null, val authorLoading: Boolean = false,
    val authorError: Throwable? = null, val followBusy: Boolean = false,
    val originalLoading: Boolean = false, val originalError: Throwable? = null,
)

class TopicViewModel(
    private val repository: CommunityRepository, val topicId: Int, private val number: Int? = null,
    viewerId: Int? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(TopicState(viewerId = viewerId))
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var moreJob: Job? = null
    private var authorJob: Job? = null
    private var originalJob: Job? = null
    private var generation = 0
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    init { refresh() }

    fun refresh() {
        if (_state.value.busy.isNotEmpty() || _state.value.followBusy) return
        refreshJob?.cancel()
        moreJob?.cancel()
        authorJob?.cancel()
        originalJob?.cancel()
        generation++
        _state.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null,
            authorLoading = false, originalLoading = false) }
        refreshJob = viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) { repository.detail(topicId, number) }
                _state.update { it.copy(detail = detail, loading = false, authorProfile = null, authorError = null, originalError = null) }
                loadAuthor()
                loadOriginal()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e, loading = false) } }
        }
    }

    fun more() {
        val snapshot = _state.value
        val detail = snapshot.detail ?: return
        if (snapshot.loadingMore || snapshot.loading) return
        val loaded = detail.posts.map { it.id }.toSet()
        val ids = detail.stream.filterNot { it in loaded }.take(20)
        if (ids.isEmpty()) return
        val requestGeneration = generation
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob = viewModelScope.launch {
            try {
                val posts = withContext(Dispatchers.IO) { repository.posts(topicId, ids) }
                val available = posts.map { it.id }.toSet()
                _state.update { current ->
                    val latest = current.detail
                    if (requestGeneration != generation || latest == null) current else current.copy(
                        detail = latest.copy(posts = (latest.posts + posts.map { incoming ->
                            latest.pinnedOriginal?.takeIf { it.id == incoming.id } ?: incoming
                        }).distinctBy(Post::id).sortedBy(Post::number),
                            stream = latest.stream.filter { id -> id !in ids || id in available }), loadingMore = false)
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loadingMore = false, moreError = e) } }
        }
    }

    private fun loadOriginal() {
        val detail = _state.value.detail ?: return
        val id = detail.stream.firstOrNull() ?: return
        if (detail.originalPost != null) return
        val requestGeneration = generation
        _state.update { it.copy(originalLoading = true, originalError = null) }
        originalJob = viewModelScope.launch {
            try {
                val post = withContext(Dispatchers.IO) { repository.posts(topicId, listOf(id)) }
                    .firstOrNull { it.id == id && it.number == 1 }
                    ?: throw ApiFailure(FailureKind.SERVER, "主帖暂不可用")
                _state.update { current ->
                    if (generation != requestGeneration) current else current.copy(originalLoading = false,
                        detail = current.detail?.copy(pinnedOriginal = post, author = post.author,
                            createdAt = current.detail.createdAt.ifBlank { post.createdAt }))
                }
                if (generation == requestGeneration && detail.author == null) loadAuthor()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(originalLoading = false, originalError = e) } }
        }
    }

    private fun loadAuthor() {
        val author = _state.value.detail?.author ?: return
        if (author.id == _state.value.viewerId) {
            _state.update { it.copy(authorProfile = author, authorLoading = false, authorError = null) }
            return
        }
        authorJob?.cancel()
        val requestGeneration = generation
        _state.update { it.copy(authorLoading = true, authorError = null) }
        authorJob = viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { repository.user(author.username) }
                if (profile.id != author.id || !profile.username.equals(author.username, ignoreCase = true)) {
                    throw ApiFailure(FailureKind.SERVER, "作者状态需重新加载")
                }
                _state.update { if (generation != requestGeneration) it else it.copy(authorProfile = profile, authorLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(authorLoading = false, authorError = e) } }
        }
    }

    fun follow() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.followBusy || snapshot.authorLoading) return
        val author = snapshot.authorProfile
        if (author == null) { loadAuthor(); return }
        if (author.id == snapshot.viewerId || (!author.canFollow && !author.isFollowed)) return
        _state.update { it.copy(followBusy = true) }
        viewModelScope.launch {
            try {
                val followed = withContext(Dispatchers.IO) { repository.follow(author) }
                _state.update { it.copy(authorProfile = author.copy(isFollowed = followed)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { messages.send(e.message ?: "关注操作失败") }
            finally { _state.update { it.copy(followBusy = false) } }
        }
    }

    fun like(post: Post) = action(post, repository::like)
    fun react(post: Post, reaction: String) = action(post) { latest ->
        val options = _state.value.detail?.validReactions.orEmpty()
        require(reaction in options || reaction == latest.activeReaction || (!latest.reactionsAvailable && reaction == "heart")) { "此回应暂不可用" }
        repository.react(latest, reaction)
    }
    fun bookmark(post: Post) = action(post) { latest -> latest.copy(bookmarkId = repository.bookmark(latest)) }

    private fun action(post: Post, block: suspend (Post) -> Post) {
        val snapshot = _state.value
        if (snapshot.loading || post.id in snapshot.busy) return
        val latest = snapshot.detail?.posts?.firstOrNull { it.id == post.id }
            ?: snapshot.detail?.originalPost?.takeIf { it.id == post.id } ?: return
        _state.update { it.copy(busy = it.busy + post.id) }
        viewModelScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) { block(latest) }
                _state.update { current -> current.copy(detail = current.detail?.let { detail ->
                    detail.copy(posts = detail.posts.map { old -> if (old.id == updated.id) updated else old },
                        pinnedOriginal = if (detail.pinnedOriginal?.id == updated.id) updated else detail.pinnedOriginal)
                }) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.send(e.message ?: "操作失败") }
            finally { _state.update { it.copy(busy = it.busy - post.id) } }
        }
    }
}
