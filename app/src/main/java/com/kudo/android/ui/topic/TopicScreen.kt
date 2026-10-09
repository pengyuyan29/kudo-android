package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.components.liquid.LiquidModal
import com.kudo.android.ui.components.liquid.LiquidModalAction

@Composable
fun TopicScreen(state: TopicState, glass: GlassMode, back: () -> Unit, retry: () -> Unit, more: () -> Unit, login: () -> Unit, like: (Post) -> Unit, bookmark: (Post) -> Unit, reply: (Post?) -> Unit, user: (String) -> Unit, image: (String) -> Unit, openLink: (String) -> Unit, share: () -> Unit, follow: () -> Unit = {}, react: (Post, String) -> Unit = { _, _ -> }, openBrowser: (String) -> Unit = openLink, loadReplies: (Post, Boolean) -> Unit = { _, _ -> }, resolveContext: () -> Unit = {}) {
    val backdrop = rememberLayerBackdrop()
    val modalBackdrop = rememberLayerBackdrop()
    val visibility = remember { MutableTransitionState(false) }
    var reactionPostId by remember { mutableStateOf<Int?>(null) }
    val threadVisibility = remember { MutableTransitionState(false) }
    var threadRootId by rememberSaveable { mutableStateOf<Int?>(null) }
    var threadTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    val reactionShowing = visibility.currentState || visibility.targetState
    val showing = reactionShowing || threadVisibility.currentState || threadVisibility.targetState
    fun openThread(root: Post, number: Int?) { threadRootId = root.id; threadTarget = number; threadVisibility.targetState = true }
    val threads = state.threads
    fun dismiss() { visibility.targetState = false }
    fun choose(post: Post) { reactionPostId = post.id; visibility.targetState = true }
    fun replyTo(post: Post?) { threadVisibility.targetState = false; visibility.targetState = false; reply(post) }
    val detail = state.detail
    val actions = LocalTopicActions.current
    val ignoredUsers = LocalIgnoredUsers.current
    var revealOriginal by remember(detail?.id, ignoredUsers) { mutableStateOf(false) }
    val originalIgnored = detail?.author?.username?.lowercase(java.util.Locale.ROOT) in ignoredUsers
    val listState = rememberLazyListState()
    var located by rememberSaveable(detail?.id, state.targetNumber) { mutableStateOf(false) }
    LaunchedEffect(threads, state.loading, state.contextLoading, state.targetNumber) {
        if (!located && !state.loading && !state.contextLoading && state.error == null && state.targetNumber != null) {
            val index = threads.indexOfFirst { it.root.number == state.targetNumber || it.replies.any { post -> post.number == state.targetNumber } }
            if (index >= 0) {
                listState.scrollToItem(index + if (!originalIgnored || revealOriginal) 1 else 0)
                val thread = threads[index]
                if (thread.root.number != state.targetNumber) openThread(thread.root, state.targetNumber)
                located = true
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(modalBackdrop)
            .then(if (showing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (showing) cancelFocusChange() } }.focusGroup()) {
            Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                TopicTopBar(state, !showing, back, user, follow) { detail?.let { actions.openDetail(it.id, it.originalPost, bookmark) } }
                LazyColumn(state = listState, modifier = Modifier.testTag("topic-posts"), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.loading && detail == null) item { LoadingRow() }
                    if (state.error != null) item { ErrorState(state.error, retry, login) }
                    if (detail != null) {
                        if (!originalIgnored || revealOriginal) item("title") {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                                if (detail.tags.isNotEmpty()) TopicTags(detail.tags)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${detail.stream.size.coerceAtLeast(detail.posts.size)} 个楼层", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (detail.closed) Text("话题已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        items(threads, key = { it.root.id }) { thread ->
                            LaunchedEffect(thread.root.id, state.contextLoading) {
                                if (!state.contextLoading && thread.root.number > 1) loadReplies(thread.root, true)
                            }
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                TopicPost(thread.root, detail, state, !showing, like, bookmark, ::replyTo, user, image, openLink, ::choose,
                                    revealOriginal) { revealOriginal = true }
                                if (thread.root.number > 1) ReplyPreview(thread, state.replyLoads[thread.root.id], state.hasMoreReplies(thread.root), !showing) {
                                    openThread(thread.root, it)
                                }
                            }
                        }
                        if (state.contextLoading) item { Text("正在补齐回复关系…", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
                        if (state.contextError != null) item { TextButton(onClick = resolveContext) { Text("部分原评论暂不可访问，点击重试") } }
                        if (state.loadingMore) item { LoadingRow() }
                        else if (state.moreError != null) item { ErrorState(state.moreError, more, login) }
                        else if (detail.stream.any { id -> state.allPosts.none { it.id == id } }) item { TextButton(onClick = more, modifier = Modifier.fillMaxWidth()) { Text("加载更多楼层（已显示 ${state.allPosts.size.coerceAtMost(detail.stream.size)} / ${detail.stream.size}）") } }
                        else item { Text("已读完全部回复", Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
            TopicBottomBar(state, backdrop, glass, !showing, { replyTo(null) }, like, bookmark, share, ::choose,
                Modifier.align(Alignment.BottomCenter))
        }
        val thread = threads.firstOrNull { it.root.id == threadRootId }
        LaunchedEffect(threadVisibility.targetState, threadRootId) {
            if (threadVisibility.targetState && thread != null) loadReplies(thread.root, false)
        }
        val threadList = rememberLazyListState()
        var threadLocated by remember(threadRootId, threadTarget) { mutableStateOf(false) }
        LaunchedEffect(thread, threadTarget, threadVisibility.targetState) {
            if (!threadLocated && threadVisibility.targetState && threadTarget != null) {
                val index = thread?.replies?.indexOfFirst { it.number == threadTarget } ?: -1
                if (index >= 0) { threadList.scrollToItem(index + 1); threadLocated = true }
            }
        }
        Box(Modifier.fillMaxSize().then(if (reactionShowing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (reactionShowing) cancelFocusChange() } }.focusGroup()) {
            LiquidModal(threadVisibility, "评论回复", true, modalBackdrop, glass, { threadVisibility.targetState = false },
                actions = { LiquidModalAction("关闭", { threadVisibility.targetState = false }, Modifier.fillMaxWidth()) }) {
                if (thread != null && detail != null) {
                    LazyColumn(state = threadList, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("thread-posts")) {
                        item("root") { TopicPost(thread.root, detail, state, !reactionShowing, like, bookmark, ::replyTo, user, image, openLink, ::choose) }
                        items(thread.replies, key = { it.id }) { post ->
                            HorizontalDivider()
                            TopicPost(post, detail, state, !reactionShowing, like, bookmark, ::replyTo, user, image, openLink, ::choose)
                        }
                        val load = state.replyLoads[thread.root.id]
                        if (load?.loading == true) item { LoadingRow() }
                        else if (load?.error != null) item { ErrorState(load.error, { loadReplies(thread.root, false) }, login) }
                        else if (state.hasMoreReplies(thread.root)) item { TextButton({ loadReplies(thread.root, false) }, Modifier.fillMaxWidth()) { Text("加载更多回复") } }
                        else item { Text("已显示全部回复", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } else Text("原评论暂不可访问，请关闭后重试")
            }
        }
        val selected = state.allPosts.firstOrNull { it.id == reactionPostId }
        LiquidModal(visibility, "回应", true, modalBackdrop, glass, ::dismiss,
            actions = { LiquidModalAction("取消", ::dismiss, Modifier.fillMaxWidth()) }) {
            if (selected != null) {
                TopicReactionChoices(selected, detail?.validReactions.orEmpty(),
                    visibility.targetState && selected.id !in state.busy && (state.viewerId == null || selected.canReact)) {
                    if (visibility.targetState) { dismiss(); react(selected, it) }
                }
            }
        }
    }
}
