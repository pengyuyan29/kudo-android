package com.kudo.android.ui

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.account.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.editor.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import com.kudo.android.ui.web.WebScreen
import kotlinx.coroutines.launch

@Composable
private inline fun <reified T : ViewModel> model(key: String, noinline create: () -> T): T = viewModel(key = key, factory = viewModelFactory { initializer { create() } })

@Composable
private fun RefreshFeedOnResume(feed: FeedViewModel, user: Member?) {
    var resumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(user?.id, user?.unifiedNew) { feed.setUser(user) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val changedAccount = feed.setUser(user)
        if (resumed && !changedAccount) feed.refresh()
        resumed = true
    }
}

@Composable
private fun ObserveHomeUpdates(feed: FeedViewModel, user: Member?) {
    LaunchedEffect(user?.id, user?.unifiedNew) { feed.setUser(user) }
    LifecycleResumeEffect(feed, user?.id, user?.unifiedNew) {
        feed.setUser(user)
        feed.setUpdatesActive(true)
        onPauseOrDispose { feed.setUpdatesActive(false) }
    }
}

@Composable
fun KudoApp(app: AppContainer, incomingUrl: String? = null, linkSequence: Long = 0L, consumeLink: () -> Unit = {}) {
    val display by app.settings.display.collectAsStateWithLifecycle()
    var darkStatusIcons by remember { mutableStateOf<Boolean?>(null) }
    KudoTheme(display, darkStatusIcons) {
        val account = model("account") { AccountViewModel(app) }
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var refreshEpoch by rememberSaveable { mutableIntStateOf(0) }
        var readingEpoch by rememberSaveable { mutableIntStateOf(0) }
        val readingSync = remember(app.repository, scope) {
            TopicReadingSync(scope, app.repository) { readingEpoch++ }
        }
        SideEffect { readingSync.setAccount(account.user?.id) }
        DisposableEffect(readingSync) { onDispose { readingSync.close() } }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        fun openBrowser(url: String) {
            if (Uri.parse(url).scheme !in listOf("https", "http")) return
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                val browser = context.packageManager.resolveActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.takeUnless { it == "android" || it == context.packageName }
                if (browser != null) context.startActivity(intent.setPackage(browser))
                else context.startActivity(Intent.createChooser(intent, "浏览器打开").putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, com.kudo.android.MainActivity::class.java))))
            }
            catch (_: Exception) { scope.launch { snackbar.showSnackbar("没有可打开此链接的浏览器") } }
        }
        fun login() { nav.navigate("login") { launchSingleTop = true } }
        fun authenticated(action: () -> Unit) { if (account.user == null) login() else action() }
        fun topic(id: Int, number: Int? = null) { if (id > 0) nav.navigate("topic/$id?post=${number ?: 0}") }
        fun user(username: String) { if (username.isNotBlank()) nav.navigate("user/${Uri.encode(username)}") }
        fun invalidLink() { scope.launch { snackbar.showSnackbar("无法打开此链接") } }
        fun openWebPage(url: String) { nav.openWebPage(url, ::invalidLink) }
        fun openLink(url: String) {
            nav.openContentLink(url, { selected = 0 }, ::invalidLink)
        }
        fun editor(id: Int? = null, number: Int? = null) { authenticated { nav.navigate("compose?topic=${id ?: 0}&reply=${number ?: 0}") } }
        LaunchedEffect(account) { account.events.collect { snackbar.showSnackbar(it) } }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { account.checkSession() } }
        IncomingLinuxDoLink(nav, incomingUrl, linkSequence, { selected = 0 }, consumeLink)
        TopicPresentationEnvironment(app.repository, account.user?.id) {
        AvatarEnvironment(app.repository, account.user?.id) {
            ImageGalleryHost(loadAlbum = { id ->
                val detail = app.repository.detail(id, 1)
                val first = detail.originalPost ?: detail.stream.firstOrNull()?.let { firstId ->
                    app.repository.posts(id, listOf(firstId)).firstOrNull { it.number == 1 }
                }
                parseArticle(first?.cooked ?: error("暂时无法读取主帖图片")).articleImages()
            }, openLink = ::openLink, accountKey = account.user?.id) {
            TopicActionsHost(app.repository, account.user, display.glass, ::login,
                ignored = { id, username -> account.recordIgnore(id, username, true); refreshEpoch++ },
                message = { snackbar.showSnackbar(it) }, changed = {
                    refreshEpoch++
                    nav.currentBackStackEntry?.savedStateHandle?.set("refresh", System.nanoTime())
                }) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                NavHost(nav, "root", Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    enterTransition = { fadeIn(tween(280)) }, exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(220)) }) {
                    composable("root") {
                        val feed = model("feed") { FeedViewModel(app.repository, initialUser = account.user, updates = DiscourseMessageBus(app.client, userAgent = app.userAgent)) }
                        val state by feed.state.collectAsStateWithLifecycle()
                        val pages by feed.pages.collectAsStateWithLifecycle()
                        val backdrop = rememberLayerBackdrop()
                        ObserveHomeUpdates(feed, account.user)
                        LaunchedEffect(refreshEpoch) { feed.refreshIfRequested(refreshEpoch) }
                        LaunchedEffect(readingEpoch, account.user?.id) {
                            feed.refreshReadListsIfRequested(readingEpoch, account.user?.id)
                        }
                        LaunchedEffect(selected, account.user?.id) {
                            if (selected == 1 && account.categories.isEmpty()) account.loadCategories()
                            if (selected == 3) account.loadNotices()
                        }
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background)) {
                                when (selected) {
                                    0 -> FeedScreen(state.forUser(account.user), account.user, feed::select, feed::refresh, feed::more, { topic(it) }, ::user, { nav.navigate("search") }, { if (account.user == null) login() else user(account.user!!.username) }, { account.loadCategories() }, listStyle = display.topicListStyle, pages = pages,
                                        categoryContent = { CategoriesScreen(account.categories, account.categoriesLoading, account.categoryError, account::loadCategories, ::login, inHome = true) { nav.navigate("category/${it.id}") } })
                                    1 -> CategoriesScreen(account.categories, account.categoriesLoading, account.categoryError, account::loadCategories, ::login) { nav.navigate("category/${it.id}") }
                                    3 -> NotificationsScreen(account, ::login, ::topic, ::openWebPage,
                                        { nav.navigate("personal/${AccountDestination.MESSAGES.name}") })
                                    4 -> ProfileScreen(account.user, ::login, { nav.navigate("settings") }, { nav.navigate("bookmarks") }, { account.user?.username?.let(::user) }, repository = app.repository, about = { nav.navigate("about") },
                                        destination = { destination ->
                                            when (destination) {
                                                AccountDestination.BOOKMARKS -> nav.navigate("bookmarks")
                                                AccountDestination.TRUST -> nav.navigate("trust")
                                                AccountDestination.TOPICS, AccountDestination.HISTORY, AccountDestination.MESSAGES -> nav.navigate("personal/${destination.name}")
                                                AccountDestination.DRAFTS -> nav.navigate("drafts")
                                                AccountDestination.CHAT, AccountDestination.PENDING, AccountDestination.BADGES, AccountDestination.INVITES -> nav.navigate("account-records/${destination.name}")
                                            }
                                        }, statusBarStyle = { darkStatusIcons = it })
                                }
                            }
                            GlassNavigation(selected, {
                                selected = it
                                if (it == 0 && state.forUser(account.user).hasUpdates) feed.refreshFromNavigation()
                            }, { editor() }, backdrop, display.glass, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).widthIn(max = 530.dp).fillMaxWidth(), homeHasUpdates = state.forUser(account.user).hasUpdates)
                        }
                    }
                    composable("category/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                        val id = entry.arguments!!.getInt("id")
                        val vm = model("category-$id") { FeedViewModel(app.repository, id, account.user) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        RefreshFeedOnResume(vm, account.user)
                        FeedScreen(state.forUser(account.user, inCategory = true), account.user, vm::select, vm::refresh, vm::more, { topic(it) }, ::user, { nav.navigate("search") }, ::login, {}, categoryTitle = account.categories.firstOrNull { it.id == id }?.name ?: "社区分类", back = { nav.popBackStack() }, listStyle = display.topicListStyle)
                    }
                    composable("search") {
                        val vm = model("search") { SearchViewModel(app.repository) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        SearchScreen(state, vm::search, { nav.popBackStack() }, { topic(it) }, ::user, ::login, listStyle = display.topicListStyle)
                    }
                    composable("personal/{destination}") { entry ->
                        val member = account.user
                        val destination = AccountDestination.entries.firstOrNull { it.name == entry.arguments?.getString("destination") }
                        if (member != null && destination != null) {
                            val vm = model("personal-${member.id}-$destination") { PersonalTopicsViewModel(app.repository, member.username, destination) }
                            PersonalTopicsScreen(vm, account.categories, display.topicListStyle, { nav.popBackStack() }, ::login, ::topic, ::user,
                                { nav.navigate("new-message") })
                        } else Column { AccountPageTopBar("个人内容", { nav.popBackStack() }); EmptyState("登录后查看", "登录后查看自己的社区内容。", actionLabel = "登录", action = ::login) }
                    }
                    composable("trust") {
                        account.user?.let { member ->
                            TrustScreen(member, app.connect, { nav.popBackStack() }, { openWebPage("https://connect.linux.do/") })
                        } ?: Column { AccountPageTopBar("等级要求", { nav.popBackStack() }); EmptyState("登录后查看等级", "查看当前账号的等级条件与进度。", actionLabel = "登录", action = ::login) }
                    }
                    composable("account-records/{destination}") { entry ->
                        val member = account.user
                        val destination = AccountDestination.entries.firstOrNull { it.name == entry.arguments?.getString("destination") }
                        if (member != null && destination != null) key(member.id, destination) {
                            AccountRecordsScreen(destination, member, app.repository, app.settings, { nav.popBackStack() }, ::login,
                                ::openWebPage, ::topic, { id -> nav.navigate("chat/$id") }, ::editor) { text ->
                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("邀请链接", text))
                                scope.launch { snackbar.showSnackbar("已复制邀请链接") }
                            }
                        } else Column { AccountPageTopBar("个人功能", { nav.popBackStack() }); EmptyState("请先登录", "登录后查看个人内容。", actionLabel = "登录", action = ::login) }
                    }
                    composable("drafts") {
                        val member = account.user
                        if (member != null) DraftsScreen(member, app.repository, app.settings, { nav.popBackStack() }, ::login) { draft ->
                            if (draft.remote) nav.navigate("cloud-draft/${Uri.encode(draft.key)}")
                            else if (draft.privateMessage) nav.navigate("new-message")
                            else editor(draft.topicId, draft.replyTo)
                        } else Column { AccountPageTopBar("我的草稿", { nav.popBackStack() }); EmptyState("登录后查看草稿", "草稿按账号分别保存。", actionLabel = "登录", action = ::login) }
                    }
                    composable("cloud-draft/{key}") { entry ->
                        account.user?.let { member ->
                            LaunchedEffect(member.id) { if (account.categories.isEmpty()) account.loadCategories() }
                            CloudDraftScreen(entry.arguments!!.getString("key")!!, member, app.repository, app.settings, account.categories,
                                { nav.popBackStack() }, ::login) { result ->
                                nav.popBackStack(); refreshEpoch++
                                if (!result.queued) result.topicId?.let { topic(it) }
                                scope.launch { snackbar.showSnackbar(if (result.queued) "已提交审核" else "发布成功") }
                            }
                        } ?: Column { AccountPageTopBar("云端草稿", { nav.popBackStack() }); EmptyState("请先登录", "登录后继续编辑。", actionLabel = "登录", action = ::login) }
                    }
                    composable("new-message") {
                        account.user?.let { member -> key(member.id) {
                            PrivateMessageScreen(member, app.repository, app.settings, { nav.popBackStack() }) { result ->
                                nav.popBackStack()
                                if (!result.queued) result.topicId?.let { topic(it) }
                                scope.launch { snackbar.showSnackbar(if (result.queued) "私信已提交审核" else "私信已发送") }
                            }
                        } } ?: Column { AccountPageTopBar("写私信", { nav.popBackStack() }); EmptyState("请先登录", "登录后发送私信。", actionLabel = "登录", action = ::login) }
                    }
                    composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                        account.user?.let { member ->
                            val id = entry.arguments!!.getInt("id")
                            val vm = model("chat-${member.id}-$id") { ChatViewModel(app.repository, id) }
                            ChatScreen(vm, member, app.settings, { nav.popBackStack() }, ::login,
                                { openWebPage("https://linux.do/chat/c/-/$id") }, ::openLink, ::user)
                        } ?: Column { AccountPageTopBar("聊天", { nav.popBackStack() }); EmptyState("请先登录", "登录后查看聊天。", actionLabel = "登录", action = ::login) }
                    }
                    composable("web?url={url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) { entry ->
                        WebScreen(entry.arguments!!.getString("url")!!, { nav.popBackStack() }, ::openBrowser)
                    }
                    composable("topic/{id}?post={post}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("post") { type = NavType.IntType; defaultValue = 0 }),
                        enterTransition = { fadeIn(tween(280)) + scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.97f) },
                        popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.98f) },
                        popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = 0.98f) }) { entry ->
                        val id = entry.arguments!!.getInt("id")
                        val number = entry.arguments!!.getInt("post").takeIf { it > 0 }
                        val vm = model("topic-$id-$number-${account.user?.id}") { TopicViewModel(app.repository, id, number, account.user?.id) }
                        val state by vm.state.collectAsStateWithLifecycle()
                        val nonce by entry.savedStateHandle.getStateFlow("refresh", 0L).collectAsStateWithLifecycle()
                        LaunchedEffect(nonce) { if (nonce != 0L) vm.refresh() }
                        LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
                        var replyTarget by rememberSaveable(account.user?.id) { mutableStateOf<Int?>(null) }
                        val replyVisibility = remember(account.user?.id) { MutableTransitionState(replyTarget != null) }
                        val keyboard = LocalSoftwareKeyboardController.current
                        val replyShowing = replyVisibility.currentState || replyVisibility.targetState
                        LaunchedEffect(replyVisibility.currentState, replyVisibility.targetState) {
                            if (!replyVisibility.currentState && !replyVisibility.targetState) replyTarget = null
                        }
                        Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().then(if (replyShowing) Modifier.clearAndSetSemantics {} else Modifier)
                            .focusProperties { onEnter = { if (replyShowing) cancelFocusChange() } }.focusGroup()) {
                        TopicScreen(state, display.glass, { nav.popBackStack() }, vm::refresh, vm::more, ::login, { authenticated { vm.like(it) } }, { authenticated { vm.bookmark(it) } }, { post -> authenticated { replyTarget = post?.number ?: 0; replyVisibility.targetState = true } }, ::user, {}, ::openLink,
                            { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${state.detail?.title.orEmpty()}\nhttps://linux.do/t/$id") }, "分享话题")) },
                            follow = { authenticated { vm.follow() } }, react = { post, reaction -> authenticated { vm.react(post, reaction) } }, openBrowser = ::openBrowser, loadReplies = vm::loadReplies, resolveContext = vm::resolveContext,
                            readingEnabled = !replyShowing, onReadingBatch = { readingSync.submit(state.viewerId, it) }, onPostsRead = vm::markRead)
                        }
                        val currentUser = account.user
                        val target = replyTarget
                        if (currentUser != null && target != null) {
                            val editorVm = model("reply-${currentUser.id}-$id-$target") { EditorViewModel(app, currentUser.id, id, target.takeIf { it > 0 }) }
                            LaunchedEffect(editorVm) { editorVm.published.collect { result ->
                                keyboard?.hide(); replyVisibility.targetState = false
                                vm.refresh(); refreshEpoch++
                                snackbar.showSnackbar(if (result.queued) "内容已提交，等待社区审核" else "发布成功")
                            } }
                            ReplyEditorSheet(replyVisibility, editorVm.sending, { replyVisibility.targetState = false }) {
                                EditorScreen(editorVm, account.categories, account.categoryError, account::loadCategories, { keyboard?.hide(); replyVisibility.targetState = false })
                            }
                        }
                        }
                    }
                    composable("login") { LoginScreen(account, { refreshEpoch++; nav.popBackStack() }, { refreshEpoch++; nav.popBackStack(); scope.launch { snackbar.showSnackbar("欢迎回来，${account.user?.displayName}") } }) }
                    composable("settings") { SettingsScreen(display, app.settings::update, account.user, account::logout, { nav.popBackStack() }, ::openWebPage, { nav.navigate("ignored-users") }) }
                    composable("about") { AboutScreen { nav.popBackStack() } }
                    composable("ignored-users") {
                        LaunchedEffect(Unit) { account.checkSession() }
                        key(account.user?.id) { IgnoredUsersScreen(account.user, app.repository, { nav.popBackStack() }, ::login) { id, username ->
                            account.recordIgnore(id, username, false); refreshEpoch++
                        } }
                    }
                    composable("bookmarks") { LaunchedEffect(account.user?.id) { account.loadSaved() }; BookmarksScreen(account, { nav.popBackStack() }, ::login, ::topic) }
                    composable("user/{username}") { entry -> UserScreen(entry.arguments!!.getString("username")!!, app.repository, { nav.popBackStack() }, ::login, ::openWebPage) }
                    composable("compose?topic={topic}&reply={reply}", arguments = listOf(navArgument("topic") { type = NavType.IntType; defaultValue = 0 }, navArgument("reply") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                        val topicId = entry.arguments!!.getInt("topic").takeIf { it > 0 }
                        val replyTo = entry.arguments!!.getInt("reply").takeIf { it > 0 }
                        val currentUser = account.user
                        if (currentUser == null) {
                            Column { PageTopBar("发布", { nav.popBackStack() }); EmptyState("登录后发布", "草稿会自动保存在本机。", actionLabel = "登录", action = ::login) }
                        } else {
                            val vm = model("editor-${currentUser.id}-$topicId-$replyTo") { EditorViewModel(app, currentUser.id, topicId, replyTo) }
                            LaunchedEffect(Unit) { if (account.categories.isEmpty()) account.loadCategories() }
                            LaunchedEffect(vm) { vm.published.collect { result ->
                                nav.previousBackStackEntry?.savedStateHandle?.set("refresh", System.nanoTime())
                                nav.popBackStack(); refreshEpoch++
                                if (!result.queued && topicId == null) result.topicId?.let { topic(it) }
                                snackbar.showSnackbar(if (result.queued) "内容已提交，等待社区审核" else "发布成功")
                            } }
                            EditorScreen(vm, account.categories, account.categoryError, account::loadCategories, { nav.popBackStack() })
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp).widthIn(max = 600.dp))
            }
            }
            }
        }
        }
    }
}
