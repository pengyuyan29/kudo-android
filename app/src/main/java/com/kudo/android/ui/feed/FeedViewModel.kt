package com.kudo.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FeedState(val topics: List<Topic> = emptyList(), val categories: List<Category> = emptyList(), val filter: String = "latest", val loading: Boolean = true, val loadingMore: Boolean = false, val next: String? = null, val error: Throwable? = null, val moreError: Throwable? = null)

class FeedViewModel(private val repository: CommunityRepository, private val categoryId: Int? = null) : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    init { refresh() }
    fun select(filter: String) {
        if (filter == _state.value.filter) return
        _state.value = FeedState(categories = _state.value.categories, filter = filter)
        refresh()
    }
    fun refresh() {
        job?.cancel()
        val filter = _state.value.filter
        _state.update { it.copy(loading = true, loadingMore = false, error = null, moreError = null) }
        job = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.topics(filter, categoryId) }
                _state.update { it.copy(topics = page.topics.distinctBy(Topic::id), next = page.next, loading = false) }
                if (_state.value.categories.isEmpty()) {
                    try {
                        val categories = withContext(Dispatchers.IO) { repository.categories() }
                        _state.update { it.copy(categories = categories) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {
                        // Category metadata is optional: keep successfully loaded topics readable.
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loading = false, error = e) } }
        }
    }
    fun more() {
        val snapshot = _state.value
        val next = snapshot.next ?: return
        if (snapshot.loading || snapshot.loadingMore) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        job = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.topics(snapshot.filter, categoryId, next) }
                _state.update { it.copy(topics = (it.topics + page.topics).distinctBy(Topic::id), next = page.next?.takeUnless { path -> path == next }, loadingMore = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loadingMore = false, moreError = e) } }
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
