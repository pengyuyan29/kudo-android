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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    KudoTheme(display) {
        val account = model("account") { AccountViewModel(app) }
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var refreshEpoch by rememberSaveable { mutableIntStateOf(0) }
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
        fun destination(target: LinuxDoDestination) {
            nav.openLinuxDoDestination(target) { selected = 0 }
        }
        fun openLink(url: String) { LinuxDoLink.parse(url)?.let(::destination) ?: openBrowser(url) }
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
                parseArticle(first?.cooked ?: error("暂时无法读取主帖图片")).filterIsInstance<ArticleBlock.Image>()
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
                                    3 -> NotificationsScreen(account, ::login, ::topic)
                                    4 -> ProfileScreen(account.user, ::login, { nav.navigate("settings") }, { nav.navigate("bookmarks") }, { account.user?.username?.let(::user) }, repository = app.repository)
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
                        TopicScreen(state, display.glass, { nav.popBackStack() }, vm::refresh, vm::more, ::login, { authenticated { vm.like(it) } }, { authenticated { vm.bookmark(it) } }, { editor(id, it?.number) }, ::user, {}, ::openLink,
                            { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${state.detail?.title.orEmpty()}\nhttps://linux.do/t/$id") }, "分享话题")) },
                            follow = { authenticated { vm.follow() } }, react = { post, reaction -> authenticated { vm.react(post, reaction) } }, openBrowser = ::openBrowser, loadReplies = vm::loadReplies, resolveContext = vm::resolveContext)
                    }
                    composable("login") { LoginScreen(account, { refreshEpoch++; nav.popBackStack() }, { refreshEpoch++; nav.popBackStack(); scope.launch { snackbar.showSnackbar("欢迎回来，${account.user?.displayName}") } }) }
                    composable("settings") { SettingsScreen(display, app.settings::update, account.user, account::logout, { nav.popBackStack() }, ::openBrowser, { nav.navigate("ignored-users") }) }
                    composable("ignored-users") {
                        LaunchedEffect(Unit) { account.checkSession() }
                        key(account.user?.id) { IgnoredUsersScreen(account.user, app.repository, { nav.popBackStack() }, ::login) { id, username ->
                            account.recordIgnore(id, username, false); refreshEpoch++
                        } }
                    }
                    composable("bookmarks") { LaunchedEffect(account.user?.id) { account.loadSaved() }; BookmarksScreen(account, { nav.popBackStack() }, ::login, ::topic) }
                    composable("user/{username}") { entry -> UserScreen(entry.arguments!!.getString("username")!!, app.repository, { nav.popBackStack() }, ::login, ::openBrowser) }
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
