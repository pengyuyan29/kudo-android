package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.components.liquid.LiquidModal
import com.kudo.android.ui.components.liquid.LiquidModalAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopicScreen(state: TopicState, glass: GlassMode, back: () -> Unit, retry: () -> Unit, more: () -> Unit, login: () -> Unit, like: (Post) -> Unit, bookmark: (Post) -> Unit, reply: (Post?) -> Unit, user: (String) -> Unit, image: (String) -> Unit, openLink: (String) -> Unit, share: () -> Unit, follow: () -> Unit = {}, react: (Post, String) -> Unit = { _, _ -> }, openBrowser: (String) -> Unit = openLink, loadReplies: (Post, Boolean) -> Unit = { _, _ -> }, resolveContext: () -> Unit = {}, readingEnabled: Boolean = true, onReadingBatch: (TopicReadingBatch) -> Unit = {}, onPostsRead: (Set<Int>) -> Unit = {}, loadBoostRules: () -> Unit = {}, boost: (Post, String, () -> Unit) -> Unit = { _, _, _ -> }, jumpTo: (Int) -> Unit = {}, vote: (Post, String, List<String>) -> Unit = { _, _, _ -> }, openCategory: (Int) -> Unit = {}, openTag: (TopicTag) -> Unit = {}) {
    val backdrop = rememberLayerBackdrop()
    val modalBackdrop = rememberLayerBackdrop()
    val visibility = remember { MutableTransitionState(false) }
    var reactionPostId by remember { mutableStateOf<Int?>(null) }
    val threadVisibility = remember { MutableTransitionState(false) }
    var threadRootId by rememberSaveable { mutableStateOf<Int?>(null) }
    val boostVisibility = remember { MutableTransitionState(false) }
    var boostPostId by rememberSaveable { mutableStateOf<Int?>(null) }
    var boostRaw by rememberSaveable(boostPostId) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val boostShowing = boostVisibility.currentState || boostVisibility.targetState
    val floorVisibility = remember { MutableTransitionState(false) }
    var floorRaw by rememberSaveable { mutableStateOf("1") }
    val floorShowing = floorVisibility.currentState || floorVisibility.targetState
    val reactionShowing = visibility.currentState || visibility.targetState
    val overlayShowing = reactionShowing || boostShowing || floorShowing
    val showing = overlayShowing || threadVisibility.currentState || threadVisibility.targetState
    fun openBoost(post: Post) {
        if (state.viewerId == null) { login(); return }
        if (!post.canBoost) return
        boostPostId = post.id; loadBoostRules(); boostVisibility.targetState = true
    }
    fun closeBoost() { keyboard?.hide(); boostVisibility.targetState = false }
    fun openThread(root: Post) { threadRootId = root.id; threadVisibility.targetState = true }
    val threads = state.threads
    val posts = state.detail?.posts.orEmpty().distinctBy(Post::id).sortedBy(Post::number)
    fun dismiss() { visibility.targetState = false }
    fun choose(post: Post) { reactionPostId = post.id; visibility.targetState = true }
    fun replyTo(post: Post?) { threadVisibility.targetState = false; visibility.targetState = false; reply(post) }
    val detail = state.detail
    val actions = LocalTopicActions.current
    val ignoredUsers = LocalIgnoredUsers.current
    var revealOriginal by remember(detail?.id, ignoredUsers) { mutableStateOf(false) }
    val originalIgnored = detail?.author?.username?.lowercase(java.util.Locale.ROOT) in ignoredUsers
    val listState = rememberLazyListState()
    var currentFloor by rememberSaveable(detail?.id) { mutableIntStateOf(state.targetNumber ?: 1) }
    val scope = rememberCoroutineScope()
    suspend fun scrollToPost(index: Int) {
        val itemIndex = index + if (!originalIgnored || revealOriginal) 1 else 0
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > itemIndex }
        delay(16)
        listState.animateScrollToItem(itemIndex)
    }
    fun navigatePost(number: Int) {
        threadVisibility.targetState = false
        val index = posts.indexOfFirst { it.number == number }
        if (index < 0) jumpTo(number)
        else scope.launch {
            scrollToPost(index)
            currentFloor = number
        }
    }
    var located by remember(detail?.id, state.targetRevision) { mutableStateOf(false) }
    LaunchedEffect(state.targetRevision) {
        if (state.targetRevision > 0) { floorVisibility.targetState = false; keyboard?.hide() }
    }
    val mainVisible by remember(listState, posts) { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { item -> posts.firstOrNull { it.id == item.key }?.number }
    } }
    LaunchedEffect(mainVisible, showing, state.jumping, located) {
        if (!showing && !state.jumping && (state.targetNumber == null || located)) mainVisible?.let { currentFloor = it }
    }
    LaunchedEffect(posts, state.loading, state.contextLoading, state.targetNumber, state.targetRevision) {
        if (!located && !state.loading && !state.jumping && !state.contextLoading && state.error == null && state.targetNumber != null) {
            val index = posts.indexOfFirst { it.number == state.targetNumber }
            if (index >= 0) {
                threadVisibility.targetState = false
                scrollToPost(index)
                currentFloor = state.targetNumber
                located = true
            }
        }
    }
    val hasMore = detail?.stream?.any { id -> posts.none { it.id == id } } == true
    val covered = LocalReadingBlocked.current
    AutomaticPostLoading(listState, "more-posts", detail?.stream to posts.map(Post::id),
        hasMore && !showing && !covered && !state.loading && !state.jumping && !state.loadingMore && !state.contextLoading &&
            state.error == null && state.moreError == null && (state.targetNumber == null || located), more)
    TopicReadingEnvironment(detail?.id ?: 0, state.viewerId, readingEnabled && detail != null && !state.loading && !state.jumping &&
        !showing && (state.targetNumber == null || located), onReadingBatch, onPostsRead) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(modalBackdrop)
            .then(if (showing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (showing) cancelFocusChange() } }.focusGroup()) {
            Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                TopicTopBar(state, !showing, back, user, follow) { detail?.let { actions.openDetail(it.id, it.originalPost, bookmark) } }
                PostReadingViewport(110.dp) { viewport ->
                LazyColumn(state = listState, modifier = Modifier.testTag("topic-posts").then(viewport), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.loading && detail == null) item { LoadingRow() }
                    if (state.error != null) item { ErrorState(state.error, retry, login, back) }
                    if (detail != null) {
                        if (!originalIgnored || revealOriginal) item("title") {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                                val category = LocalTopicPresentation.current.categories.firstOrNull { it.id == detail.categoryId }
                                if (category != null || detail.tags.isNotEmpty()) {
                                    FlowRow(Modifier.testTag("topic-detail-metadata"),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                        itemVerticalAlignment = Alignment.CenterVertically) {
                                        category?.let {
                                            CategoryInline(it, Modifier.testTag("topic-detail-category"), onClick = { openCategory(it.id) })
                                        }
                                        detail.tags.forEach { tag -> TopicTagChip(tag, onClick = { openTag(tag) }) }
                                    }
                                }
                                if (detail.closed) Text("话题已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        items(posts, key = { it.id }) { post ->
                            val thread = threads.firstOrNull { it.root.id == post.id }
                            LaunchedEffect(post.id, state.contextLoading) {
                                if (!state.contextLoading && post.number > 1 && thread != null) loadReplies(post, true)
                            }
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                TopicPost(post, detail, state, !showing, like, bookmark, ::replyTo, user, image, openLink, ::choose,
                                    revealOriginal, revealOriginal = { revealOriginal = true }, boost = ::openBoost, vote = vote, login = login)
                                if (post.number > 1 && thread != null) ReplyPreview(thread, state.replyLoads[post.id], state.hasMoreReplies(post), !showing) {
                                    if (it == null) openThread(post) else navigatePost(it)
                                }
                            }
                        }
                        if (state.contextLoading) item { Text("正在补齐回复关系…", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
                        if (state.contextError != null) item { TextButton(onClick = resolveContext) { Text("部分原评论暂不可访问，点击重试") } }
                        if (state.loadingMore) item { LoadingRow() }
                        else if (state.moreError != null) item { ErrorState(state.moreError, more, login) }
                        else if (hasMore) item("more-posts") { Text("上拉继续加载楼层", Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                        else item { Text("已读完全部回复", Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
                }
            }
            TopicBottomBar(state, backdrop, glass, !showing, { replyTo(null) }, like, bookmark, share, ::choose,
                Modifier.align(Alignment.BottomCenter), boost = ::openBoost, currentFloor = currentFloor,
                floors = { floorRaw = currentFloor.toString(); floorVisibility.targetState = true })
        }
        val thread = threads.firstOrNull { it.root.id == threadRootId }
        LaunchedEffect(threadVisibility.targetState, threadRootId) {
            if (threadVisibility.targetState && thread != null) loadReplies(thread.root, false)
        }
        val threadList = rememberLazyListState()
        LaunchedEffect(threadRootId) { threadList.scrollToItem(0) }
        val threadLoad = thread?.let { state.replyLoads[it.root.id] }
        AutomaticPostLoading(threadList, "more-replies", threadRootId to threadLoad?.cursors,
            threadVisibility.targetState && !overlayShowing && !covered && thread != null && state.hasMoreReplies(thread.root) &&
                !state.loading && !state.jumping && !state.contextLoading && threadLoad?.loading != true && threadLoad?.error == null) { thread?.let { loadReplies(it.root, false) } }
        Box(Modifier.fillMaxSize().then(if (overlayShowing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (overlayShowing) cancelFocusChange() } }.focusGroup()) {
            LiquidModal(threadVisibility, "评论回复", true, modalBackdrop, glass, { threadVisibility.targetState = false }) {
                if (thread != null && detail != null) {
                    LazyColumn(state = threadList, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("thread-posts")) {
                        item("root") { ThreadPreviewPost(thread.root, !overlayShowing, image, openLink, ::navigatePost) }
                        items(thread.replies, key = { it.id }) { post ->
                            HorizontalDivider()
                            ThreadPreviewPost(post, !overlayShowing, image, openLink, ::navigatePost)
                        }
                        val load = state.replyLoads[thread.root.id]
                        if (load?.loading == true) item { LoadingRow() }
                        else if (load?.error != null) item { ErrorState(load.error, { loadReplies(thread.root, false) }, login) }
                        else if (state.hasMoreReplies(thread.root)) item("more-replies") { Text("上拉继续加载回复", Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else item { Text("已显示全部回复", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } else Text("原评论暂不可访问，请关闭后重试")
            }
        }
        val selected = state.allPosts.firstOrNull { it.id == reactionPostId }
        LiquidModal(visibility, "回应", true, modalBackdrop, glass, ::dismiss) {
            if (selected != null) {
                TopicReactionChoices(selected, detail?.validReactions.orEmpty(),
                    visibility.targetState && selected.id !in state.busy && (state.viewerId == null || selected.canReact)) {
                    if (visibility.targetState) { dismiss(); react(selected, it) }
                }
            }
        }
        val boostPost = state.allPosts.firstOrNull { it.id == boostPostId }
        val boosting = boostPostId in state.busy
        CommunitySheet(boostVisibility, "Boost", { if (!boosting) closeBoost() }, showTitle = false,
            dismissEnabled = !boosting, scrollContent = false, showActions = false) {
            BoostForm(boostRaw, { boostRaw = it }, state, boosting,
                send = { boostPost?.let { post -> boost(post, boostRaw) { boostRaw = ""; closeBoost() } } },
                close = ::closeBoost, retry = loadBoostRules, username = boostPost?.author?.username.orEmpty())
        }
        CommunitySheet(floorVisibility, "楼层跳转", { if (!state.jumping) { keyboard?.hide(); floorVisibility.targetState = false } },
            dismissEnabled = !state.jumping, showActions = false) {
            FloorNavigationForm(floorRaw, { floorRaw = it }, detail?.highestPostNumber ?: 1, state.jumping, state.jumpError, jumpTo)
        }
    }
    }
}

@Composable
private fun AutomaticPostLoading(list: LazyListState, footer: String, progress: Any?, enabled: Boolean, load: () -> Unit) {
    val latestLoad by rememberUpdatedState(load)
    LaunchedEffect(list, footer, progress, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layout = list.layoutInfo
            layout.visibleItemsInfo.any { it.key == footer && it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset }
        }.first { it }
        latestLoad()
    }
}
