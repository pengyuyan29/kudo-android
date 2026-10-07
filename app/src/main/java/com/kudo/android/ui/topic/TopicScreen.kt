package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.components.*

@Composable
fun TopicScreen(state: TopicState, glass: GlassMode, back: () -> Unit, retry: () -> Unit, more: () -> Unit, login: () -> Unit, like: (Post) -> Unit, bookmark: (Post) -> Unit, reply: (Post?) -> Unit, user: (String) -> Unit, image: (String) -> Unit, openLink: (String) -> Unit, share: () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val detail = state.detail
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            PageTopBar("话题详情", back) {
                IconButton(onClick = { openLink("https://linux.do/t/${detail?.id ?: 0}") }, enabled = detail != null) { Icon(Icons.Rounded.OpenInBrowser, "浏览器打开") }
                IconButton(onClick = retry) { Icon(Icons.Rounded.Refresh, "刷新话题") }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading && detail == null) item { LoadingRow() }
                if (state.error != null) item { ErrorState(state.error, retry, login) }
                if (detail != null) {
                    item("title") {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${detail.stream.size.coerceAtLeast(detail.posts.size)} 个楼层", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (detail.closed) Text("话题已关闭", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(detail.posts, key = { it.id }) { post ->
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(post.author, 38.dp) { user(post.author.username) }
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(post.author.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        if (post.number == 1) Text("楼主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(relativeTime(post.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("#${post.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            post.replyTo?.let { Text("回复 #$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            RichContent(post.cooked, image, openLink)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (post.number == 1) "正文" else "回复", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                TextButton(onClick = { like(post) }, enabled = post.id !in state.busy) { Icon(if (post.liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, "点赞", Modifier.size(19.dp)); Spacer(Modifier.width(5.dp)); Text(post.likes.toString()) }
                                IconButton(onClick = { bookmark(post) }, enabled = post.id !in state.busy) { Icon(if (post.bookmarkId != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "收藏此楼层", Modifier.size(20.dp)) }
                                TextButton(onClick = { reply(post) }, enabled = !detail.closed) { Text("回复") }
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
        if (detail != null) {
            val first = detail.posts.firstOrNull { it.number == 1 }
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { reply(null) }, enabled = !detail.closed, modifier = Modifier.weight(0.95f).height(57.dp).glass(backdrop, glass)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("写回复", fontWeight = FontWeight.Medium)
                }
                Row(Modifier.weight(1.3f).height(57.dp).glass(backdrop, glass), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { first?.let(like) }, enabled = first != null && first.id !in state.busy) { Icon(if (first?.liked == true) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, "赞主帖") }
                    IconButton(onClick = { first?.let(bookmark) }, enabled = first != null && first.id !in state.busy) { Icon(if (first?.bookmarkId != null) Icons.Rounded.Star else Icons.Rounded.StarOutline, "收藏主帖") }
                    IconButton(onClick = share) { Icon(Icons.Rounded.Share, "分享话题") }
                }
            }
        }
    }
}
