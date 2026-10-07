package com.kudo.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FeedState(val topics: List<Topic> = emptyList(), val categories: List<Category> = emptyList(), val filter: String = "latest", val loading: Boolean = true, val loadingMore: Boolean = false, val next: String? = null, val error: Throwable? = null, val moreError: Throwable? = null, val accountId: Int? = null, val newCount: Int? = null) {
    fun forUser(user: Member?, inCategory: Boolean = false): FeedState =
        if (accountId == user?.id) this else FeedState(filter = defaultFeedFilter(user, inCategory), accountId = user?.id)
}

private fun defaultFeedFilter(user: Member?, inCategory: Boolean) = if (user != null && !inCategory) "unread" else "latest"

class FeedViewModel(private val repository: CommunityRepository, private val categoryId: Int? = null, initialUser: Member? = null) : ViewModel() {
    private var user = initialUser
    private val _state = MutableStateFlow(FeedState(filter = defaultFeedFilter(user, categoryId != null), accountId = user?.id))
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    init { refresh() }
    fun setUser(value: Member?): Boolean {
        if (value?.id == user?.id && value?.unifiedNew == user?.unifiedNew) return false
        user = value
        _state.value = FeedState(filter = defaultFeedFilter(value, categoryId != null), accountId = value?.id)
        refresh()
        return true
    }
    fun select(filter: String) {
        if (FeedFilter.available(user != null, categoryId != null).none { it.value == filter }) return
        if (filter == _state.value.filter) return
        _state.value = FeedState(categories = _state.value.categories, filter = filter, accountId = user?.id)
        refresh()
    }
    fun refresh() {
        job?.cancel()
        moreJob?.cancel()
        val token = ++generation
        val account = user
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null, newCount = null) }
        job = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.topics(filter, categoryId) }
                if (token != generation) return@launch
                _state.update { it.copy(topics = page.topics.distinctBy(Topic::id), next = page.next, loading = false) }
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
