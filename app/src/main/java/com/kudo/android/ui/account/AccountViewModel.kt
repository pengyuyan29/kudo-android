package com.kudo.android.ui.account

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class AccountViewModel(
    private val repository: CommunityRepository,
    private val resetSession: () -> Unit,
    private val clearSession: suspend () -> Unit,
) : ViewModel() {
    constructor(app: AppContainer) : this(app.repository, app.client::resetSession, {
        app.cookies.clear()
        app.client.resetSession()
        app.settings.clearAccountData()
        android.webkit.WebStorage.getInstance().deleteAllData()
    })
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
    var noticesLoaded by mutableStateOf(false); private set
    var saved by mutableStateOf<List<SavedTopic>>(emptyList()); private set
    var savedError by mutableStateOf<Throwable?>(null); private set
    var savedLoading by mutableStateOf(false); private set
    var hasMoreSaved by mutableStateOf(false); private set
    private var savedPage = 0
    private var noticeOffset = 0
    private var noticeNext: String? = null
    private var sessionEpoch = 0
    private val messages = Channel<String>(Channel.BUFFERED)
    private var noticeJob: Job? = null
    private var savedJob: Job? = null
    val events = messages.receiveAsFlow()

    init { viewModelScope.launch { checkSession() } }

    private fun acceptSession(member: Member?) {
        if (user?.id != member?.id || user?.username != member?.username) {
            sessionEpoch++
            noticeJob?.cancel(); savedJob?.cancel()
            notices = emptyList(); saved = emptyList()
            noticesError = null; savedError = null; noticesLoading = false; savedLoading = false
            hasMoreNotices = true; noticesLoaded = false; hasMoreSaved = false; noticeOffset = 0; noticeNext = null; savedPage = 0
        }
        user = member
        checkingSession = false
    }

    fun recordIgnore(viewerId: Int, username: String, ignored: Boolean) {
        val current = user?.takeIf { it.id == viewerId } ?: return
        val names = current.ignoredUsers.filterNot { it.equals(username, ignoreCase = true) }.toSet()
        user = current.copy(ignoredUsers = if (ignored) names + username else names)
    }

    suspend fun checkSession(): Boolean {
        if (checkingSession) return false
        val epoch = sessionEpoch
        checkingSession = true; sessionError = null
        return try {
            resetSession()
            val receivedUser = withContext(Dispatchers.IO) { repository.currentUser() }
            if (epoch != sessionEpoch) false else { acceptSession(receivedUser); receivedUser != null }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (epoch == sessionEpoch) {
                if ((e as? ApiFailure)?.kind == FailureKind.SIGN_IN) acceptSession(null) else sessionError = e
            }
            false
        } finally { if (epoch == sessionEpoch) checkingSession = false }
    }
    fun loadCategories() {
        if (categoriesLoading) return
        categoriesLoading = true; categoryError = null
        viewModelScope.launch {
            try { categories = withContext(Dispatchers.IO) { repository.categories() } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { categoryError = e }
            finally { categoriesLoading = false }
        }
    }
    fun loadNotices(more: Boolean = false) {
        if (user == null || noticesLoading) return
        val epoch = sessionEpoch
        noticesLoading = true; noticesError = null
        val offset = if (more) noticeOffset else 0
        noticeJob = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.noticePage(offset, if (more) noticeNext else null) }
                if (epoch == sessionEpoch) {
                    notices = (if (more) notices + page.notices else page.notices).distinctBy(Notice::id)
                    hasMoreNotices = page.next != null; noticeNext = page.next; noticeOffset = offset + 30; noticesLoaded = true
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (epoch == sessionEpoch) noticesError = e }
            finally { if (epoch == sessionEpoch) noticesLoading = false }
        }
    }
    fun markRead(id: Int? = null) {
        if (user == null) return
        val epoch = sessionEpoch
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.readNotification(id) }
                if (epoch == sessionEpoch) notices = notices.map { if (id == null || it.id == id) it.copy(read = true) else it }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (epoch == sessionEpoch) messages.send(e.message ?: "标记失败") }
        }
    }
    fun loadSaved(more: Boolean = false) {
        val username = user?.username ?: return
        if (savedLoading) return
        val epoch = sessionEpoch
        savedLoading = true; savedError = null
        val page = if (more) savedPage + 1 else 0
        savedJob = viewModelScope.launch {
            try {
                val (result, hasMore) = withContext(Dispatchers.IO) { repository.bookmarks(username, page) }
                if (epoch == sessionEpoch) { saved = (if (more) saved + result else result).distinctBy(SavedTopic::id); hasMoreSaved = hasMore; savedPage = page }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { if (epoch == sessionEpoch) savedError = e }
            finally { if (epoch == sessionEpoch) savedLoading = false }
        }
    }
    fun logout() {
        val name = user?.username
        sessionEpoch++
        checkingSession = true
        user = null; notices = emptyList(); saved = emptyList(); sessionError = null
        noticesError = null; savedError = null; noticesLoading = false; savedLoading = false
        viewModelScope.launch {
            if (name != null) withTimeoutOrNull(5000) {
                try { withContext(Dispatchers.IO) { repository.client.write("DELETE", "/session/${android.net.Uri.encode(name)}") } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            try { clearSession() } finally { checkingSession = false }
            messages.send("已退出登录并清除本机账号数据")
        }
    }
}
