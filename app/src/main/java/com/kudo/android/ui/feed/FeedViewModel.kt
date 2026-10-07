package com.kudo.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first

data class FeedState(val topics: List<Topic> = emptyList(), val categories: List<Category> = emptyList(), val filter: String = "latest", val loading: Boolean = true, val loadingMore: Boolean = false, val next: String? = null, val error: Throwable? = null, val moreError: Throwable? = null, val accountId: Int? = null, val newCount: Int? = null, val hasUpdates: Boolean = false, val scrollToTopRequest: Long = 0) {
    fun forUser(user: Member?, inCategory: Boolean = false): FeedState =
        if (accountId == user?.id) this else FeedState(filter = defaultFeedFilter(user, inCategory), accountId = user?.id)
}

private fun defaultFeedFilter(user: Member?, inCategory: Boolean) = if (user != null && !inCategory) "unread" else "latest"

class FeedViewModel(private val repository: CommunityRepository, private val categoryId: Int? = null, initialUser: Member? = null, private val updates: FeedUpdates? = null, private val updateIntervalMillis: Long = 15_000) : ViewModel() {
    private var user = initialUser
    private val _state = MutableStateFlow(FeedState(filter = defaultFeedFilter(user, categoryId != null), accountId = user?.id))
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    private var updateJob: Job? = null
    private var updatesActive = false
    private var signals: Channel<Unit>? = null
    private var arrivalRevision = 0L
    private var baseline: Map<Int, Topic>? = null
    private var acknowledgeOnProbe = false
    private var lastRefreshEpoch = 0
    init { refresh() }
    fun setUser(value: Member?): Boolean {
        if (value?.id == user?.id && value?.unifiedNew == user?.unifiedNew) return false
        user = value
        baseline = null
        acknowledgeOnProbe = false
        _state.value = FeedState(filter = defaultFeedFilter(value, categoryId != null), accountId = value?.id)
        refresh()
        restartUpdates()
        return true
    }
    fun select(filter: String) {
        if (FeedFilter.available(user != null, categoryId != null).none { it.value == filter }) return
        if (filter == _state.value.filter) return
        baseline = null
        acknowledgeOnProbe = false
        _state.value = FeedState(categories = _state.value.categories, filter = filter, accountId = user?.id)
        refresh()
        restartUpdates()
    }
    fun refreshFromNavigation() {
        _state.update { it.copy(scrollToTopRequest = it.scrollToTopRequest + 1) }
        if (!_state.value.loading) refresh()
    }

    fun refreshIfRequested(epoch: Int) {
        if (epoch <= 0 || epoch == lastRefreshEpoch) return
        lastRefreshEpoch = epoch
        refresh()
    }

    fun setUpdatesActive(active: Boolean) {
        if (updatesActive == active) return
        updatesActive = active
        restartUpdates()
    }

    private fun restartUpdates() {
        updateJob?.cancel()
        signals?.close()
        signals = null
        val source = updates ?: return
        if (!updatesActive) return
        val channel = Channel<Unit>(Channel.CONFLATED)
        signals = channel
        val account = user
        updateJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    source.observe(account).collect {
                        arrivalRevision++
                        channel.trySend(Unit)
                    }
                }
                // Reconcile gaps after backgrounding, even when private push authentication is unavailable.
                channel.trySend(Unit)
                for (signal in channel) {
                    delay(updateIntervalMillis)
                    while (channel.tryReceive().isSuccess) { /* coalesce a busy site's messages */ }
                    state.first { !it.loading }
                    val previous = baseline ?: continue
                    val token = generation
                    val filter = _state.value.filter
                    try {
                        val page = withContext(Dispatchers.IO) { repository.topics(filter, categoryId) }
                        if (token != generation) { channel.trySend(Unit); continue }
                        val changed = page.topics.any { topic ->
                            val old = previous[topic.id]
                            old == null || topic.hasNewContentComparedTo(old)
                        }
                        _state.update { it.copy(hasUpdates = changed || (!acknowledgeOnProbe && it.hasUpdates)) }
                        acknowledgeOnProbe = false
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        // A failed background check must never erase an existing refresh prompt.
                        val retry = (e as? ApiFailure)?.retryAfter?.toLong()?.times(1000) ?: updateIntervalMillis
                        delay(retry.coerceAtLeast(updateIntervalMillis))
                        channel.trySend(Unit)
                    }
                }
            }
        }
    }

    fun refresh() {
        job?.cancel()
        moreJob?.cancel()
        val token = ++generation
        val refreshRevision = arrivalRevision
        val account = user
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null, newCount = null) }
        job = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.topics(filter, categoryId) }
                if (token != generation) return@launch
                baseline = page.topics.associateBy(Topic::id)
                acknowledgeOnProbe = arrivalRevision != refreshRevision
                _state.update { it.copy(topics = page.topics.distinctBy(Topic::id), next = page.next, loading = false, hasUpdates = it.hasUpdates && acknowledgeOnProbe) }
                if (acknowledgeOnProbe) signals?.trySend(Unit)
                coroutineScope {
                    if (_state.value.categories.isEmpty()) launch {
                        try {
                            val categories = withContext(Dispatchers.IO) { repository.categories() }
                            if (token == generation) _state.update { it.copy(categories = categories) }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) {
                            // Optional metadata must not replace a successful feed with an error.
                        }
                    }
                    if (account != null && categoryId == null) launch {
                        try {
                            val count = withContext(Dispatchers.IO) { repository.newTopicCount(account) }
                            if (token == generation) _state.update { it.copy(newCount = count) }
                        } catch (e: CancellationException) { throw e } catch (_: Exception) {
                            // Unknown is different from zero; leave the badge absent.
                        }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (token == generation) _state.update { it.copy(loading = false, error = e) } }
        }
    }
    fun more() {
        val snapshot = _state.value
        val next = snapshot.next ?: return
        if (snapshot.loading || snapshot.loadingMore) return
        val token = generation
        _state.update { it.copy(loadingMore = true, moreError = null) }
        moreJob = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.topics(snapshot.filter, categoryId, next) }
                if (token == generation) _state.update { it.copy(topics = (it.topics + page.topics).distinctBy(Topic::id), next = page.next?.takeUnless { path -> path == next }, loadingMore = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (token == generation) _state.update { it.copy(loadingMore = false, moreError = e) } }
        }
    }
}

private fun Topic.hasNewContentComparedTo(old: Topic): Boolean = bumpedAt != old.bumpedAt || replies != old.replies ||
    title != old.title || excerpt != old.excerpt || image != old.image || categoryId != old.categoryId || tags != old.tags || closed != old.closed

data class SearchState(val query: String = "", val results: List<Topic> = emptyList(), val loading: Boolean = false, val searched: Boolean = false, val hasMore: Boolean = false, val page: Int = 1, val error: Throwable? = null)
class SearchViewModel(private val repository: CommunityRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    fun search(query: String, more: Boolean = false) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || (more && _state.value.loading)) return
        job?.cancel()
        val page = if (more) _state.value.page + 1 else 1
        _state.update { if (more) it.copy(loading = true, error = null) else SearchState(query = trimmed, loading = true, searched = true) }
        job = viewModelScope.launch {
            try {
                val (topics, hasMore) = withContext(Dispatchers.IO) { repository.search(trimmed, page) }
                _state.update { it.copy(results = (if (more) it.results + topics else topics).distinctBy(Topic::id), loading = false, page = page, hasMore = hasMore) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loading = false, error = e) } }
        }
    }
}
