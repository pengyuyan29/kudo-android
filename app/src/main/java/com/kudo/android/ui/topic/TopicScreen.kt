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
fun TopicScreen(state: TopicState, glass: GlassMode, back: () -> Unit, retry: () -> Unit, more: () -> Unit, login: () -> Unit, like: (Post) -> Unit, bookmark: (Post) -> Unit, reply: (Post?) -> Unit, user: (String) -> Unit, image: (String) -> Unit, openLink: (String) -> Unit, share: () -> Unit, follow: () -> Unit = {}, react: (Post, String) -> Unit = { _, _ -> }) {
    val backdrop = rememberLayerBackdrop()
    val modalBackdrop = rememberLayerBackdrop()
    val visibility = remember { MutableTransitionState(false) }
    var reactionPostId by remember { mutableStateOf<Int?>(null) }
    val showing = visibility.currentState || visibility.targetState
    fun dismiss() { visibility.targetState = false }
    fun choose(post: Post) { reactionPostId = post.id; visibility.targetState = true }
    val detail = state.detail
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(modalBackdrop)
            .then(if (showing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (showing) cancelFocusChange() } }.focusGroup()) {
            Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                TopicTopBar(state, !showing, back, user, follow) { reactionPostId = null; visibility.targetState = true }
                LazyColumn(state = listState, modifier = Modifier.testTag("topic-posts"), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.loading && detail == null) item { LoadingRow() }
                    if (state.error != null) item { ErrorState(state.error, retry, login) }
                    if (detail != null) {
                        item("title") {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${detail.stream.size.coerceAtLeast(detail.posts.size)} 个楼层", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (detail.closed) Text("话题已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        items(detail.posts, key = { it.id }) { post ->
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (post.number != 1) Row(Modifier.heightIn(min = 48.dp).clickable(enabled = !showing) { user(post.author.username) }, verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(post.author, 32.dp)
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(post.author.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                            if (post.author.id == detail.author?.id) Text("楼主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(relativeTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("#${post.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                post.replyTo?.let { Text("回复 #$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                RichContent(post.cooked, image, openLink, galleryKey = "post-${post.id}", galleryBottomInset = 110.dp)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (post.number == 1) "正文" else "回复", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    TopicReactionButton(post, !showing && !state.loading && post.id !in state.busy && (state.viewerId == null || post.canReact),
                                        choose = if (post.reactionsAvailable && detail.validReactions.isNotEmpty()) ({ choose(post) }) else null,
                                        click = { like(post) })
                                    IconButton(onClick = { bookmark(post) }, enabled = !showing && post.id !in state.busy) { Icon(if (post.bookmarkId != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "收藏此楼层", Modifier.size(20.dp)) }
                                    TextButton(onClick = { reply(post) }, enabled = !showing && !detail.closed) { Text("回复") }
                                }
                            }
                        }
                        if (state.loadingMore) item { LoadingRow() }
                        else if (state.moreError != null) item { ErrorState(state.moreError, more, login) }
                        else if (detail.stream.any { id -> detail.posts.none { it.id == id } }) item { TextButton(onClick = more, modifier = Modifier.fillMaxWidth()) { Text("加载更多楼层（已显示 ${detail.posts.size} / ${detail.stream.size}）") } }
                        else item { Text("已读完全部回复", Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
            TopicBottomBar(state, backdrop, glass, !showing, { reply(null) }, like, bookmark, share, ::choose,
                Modifier.align(Alignment.BottomCenter))
        }
        val selected = detail?.posts?.firstOrNull { it.id == reactionPostId }
            ?: detail?.originalPost?.takeIf { it.id == reactionPostId }
        LiquidModal(visibility, if (reactionPostId == null) "更多" else "回应", true, modalBackdrop, glass, ::dismiss,
            actions = { LiquidModalAction("取消", ::dismiss, Modifier.fillMaxWidth()) }) {
            if (reactionPostId == null) {
                TextButton(onClick = { if (visibility.targetState) { dismiss(); share() } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text("分享话题")
                }
                TextButton(onClick = { if (visibility.targetState) { dismiss(); retry() } }, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && state.busy.isEmpty() && !state.followBusy) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text("刷新话题")
                }
                TextButton(onClick = { if (visibility.targetState && detail != null) { dismiss(); openLink("https://linux.do/t/${detail.id}") } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text("浏览器打开")
                }
            } else if (selected != null) {
                TopicReactionChoices(selected, detail?.validReactions.orEmpty(),
                    visibility.targetState && selected.id !in state.busy && (state.viewerId == null || selected.canReact)) {
                    if (visibility.targetState) { dismiss(); react(selected, it) }
                }
            }
        }
    }
}
