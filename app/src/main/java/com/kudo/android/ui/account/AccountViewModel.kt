package com.kudo.android.ui.account

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class AccountViewModel(val app: AppContainer) : ViewModel() {
    var user by mutableStateOf<Member?>(null); private set
    var checkingSession by mutableStateOf(false); private set
    var sessionError by mutableStateOf<Throwable?>(null); private set
    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var categoryError by mutableStateOf<Throwable?>(null); private set
    var categoriesLoading by mutableStateOf(false); private set
    var notices by mutableStateOf<List<Notice>>(emptyList()); private set
    var noticesError by mutableStateOf<Throwable?>(null); private set
    var noticesLoading by mutableStateOf(false); private set
    var hasMoreNotices by mutableStateOf(true); private set
    var saved by mutableStateOf<List<SavedTopic>>(emptyList()); private set
    var savedError by mutableStateOf<Throwable?>(null); private set
    var savedLoading by mutableStateOf(false); private set
    var hasMoreSaved by mutableStateOf(false); private set
    private var savedPage = 0
    private var noticeOffset = 0
    private var sessionEpoch = 0
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init { viewModelScope.launch { checkSession() } }

    suspend fun checkSession(): Boolean {
        if (checkingSession) return false
        checkingSession = true; sessionError = null
        return try {
            app.client.resetSession()
            user = withContext(Dispatchers.IO) { app.repository.currentUser() }
            user != null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if ((e as? ApiFailure)?.kind == FailureKind.SIGN_IN) user = null else sessionError = e
            false
        } finally { checkingSession = false }
    }
    fun loadCategories() {
        if (categoriesLoading) return
        categoriesLoading = true; categoryError = null
        viewModelScope.launch {
            try { categories = withContext(Dispatchers.IO) { app.repository.categories() } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { categoryError = e }
            finally { categoriesLoading = false }
        }
    }
    fun loadNotices(more: Boolean = false) {
        if (user == null || noticesLoading) return
        val epoch = sessionEpoch
        noticesLoading = true; noticesError = null
        val offset = if (more) noticeOffset else 0
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { app.repository.notifications(offset) }
                if (epoch == sessionEpoch) { notices = (if (more) notices + result else result).distinctBy(Notice::id); hasMoreNotices = result.size == 30; noticeOffset = offset + result.size }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (epoch == sessionEpoch) noticesError = e }
            finally { if (epoch == sessionEpoch) noticesLoading = false }
        }
    }
    fun markRead(id: Int? = null) {
        val epoch = sessionEpoch
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.repository.readNotification(id) }
                if (epoch == sessionEpoch) notices = notices.map { if (id == null || it.id == id) it.copy(read = true) else it }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { messages.send(e.message ?: "标记失败") }
        }
    }
    fun loadSaved(more: Boolean = false) {
        val username = user?.username ?: return
        if (savedLoading) return
        val epoch = sessionEpoch
        savedLoading = true; savedError = null
        val page = if (more) savedPage + 1 else 0
        viewModelScope.launch {
            try {
                val (result, hasMore) = withContext(Dispatchers.IO) { app.repository.bookmarks(username, page) }
                if (epoch == sessionEpoch) { saved = (if (more) saved + result else result).distinctBy(SavedTopic::id); hasMoreSaved = hasMore; savedPage = page }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (epoch == sessionEpoch) savedError = e }
            finally { if (epoch == sessionEpoch) savedLoading = false }
        }
    }
    fun logout() {
        val name = user?.username
        sessionEpoch++
        user = null; notices = emptyList(); saved = emptyList(); sessionError = null
        noticesError = null; savedError = null; noticesLoading = false; savedLoading = false
        viewModelScope.launch {
            if (name != null) withTimeoutOrNull(5000) {
                try { withContext(Dispatchers.IO) { app.client.write("DELETE", "/session/${android.net.Uri.encode(name)}") } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            app.cookies.clear(); app.client.resetSession(); app.settings.clearAccountData()
            android.webkit.WebStorage.getInstance().deleteAllData()
            messages.send("已退出登录并清除本机账号数据")
        }
    }
}
