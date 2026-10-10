package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import java.util.Locale

@Composable
internal fun TopicPost(post: Post, detail: TopicDetail, state: TopicState, enabled: Boolean,
    like: (Post) -> Unit, bookmark: (Post) -> Unit, reply: (Post?) -> Unit,
    user: (String) -> Unit, image: (String) -> Unit, openLink: (String) -> Unit, choose: (Post) -> Unit,
    originalRevealed: Boolean = false, revealOriginal: () -> Unit = {}) {
    val ignoredUsers = LocalIgnoredUsers.current
    var revealed by remember(post.id, ignoredUsers) { mutableStateOf(false) }
    val ignored = post.author.username.lowercase(Locale.ROOT) in ignoredUsers
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp).testTag("post-${post.number}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ignored && !(if (post.number == 1) originalRevealed else revealed)) {
            Text("@${post.author.username} 的内容已忽略", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { if (post.number == 1) revealOriginal() else revealed = true }) { Text("查看此楼层") }
        } else {
            if (post.number != 1) Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(enabled = enabled) { user(post.author.username) }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.author, 32.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(post.author.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (post.author.id == detail.author?.id) Text("楼主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    TopicAuthorMetadata(post.updatedAt, LocalTopicPresentation.current.categories.firstOrNull { it.id == detail.categoryId }, post.edited)
                }
                }
                Text("#${post.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PostUnreadIndicator(post, state)
                val actions = LocalTopicActions.current
                IconButton(onClick = { actions.openReply(detail.id, post, bookmark) }, enabled = enabled && !state.loading && post.id !in state.busy) {
                    Icon(Icons.Rounded.MoreHoriz, "更多操作", Modifier.size(20.dp))
                }
            }
            PostReadingContent(post.number, enabled && !state.loading) {
                RichContent(post.cooked, image, openLink, galleryKey = "post-${post.id}", galleryBottomInset = 110.dp)
            }
            UserSignature(post, openLink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (post.number == 1) "正文" else "回复", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                TopicReactionButton(post, enabled && !state.loading && post.id !in state.busy && (state.viewerId == null || post.canReact),
                    choose = if (post.reactionsAvailable && detail.validReactions.isNotEmpty()) ({ choose(post) }) else null,
                    click = { like(post) })
                IconButton(onClick = { bookmark(post) }, enabled = enabled && post.id !in state.busy) { Icon(if (post.bookmarkId != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "收藏此楼层", Modifier.size(20.dp)) }
                TextButton(onClick = { reply(post) }, enabled = enabled && !detail.closed) { Text("回复") }
            }
        }
    }
}

@Composable
internal fun ReplyPreview(thread: PostThread, load: ReplyLoad?, hasMore: Boolean, enabled: Boolean, open: (Int?) -> Unit) {
    if (thread.replies.isEmpty() && !hasMore && load?.loading != true && load?.error == null) return
    val ignored = LocalIgnoredUsers.current
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
        .testTag("reply-preview-${thread.root.number}").padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        thread.replies.take(3).forEach { post ->
            val hidden = post.author.username.lowercase(Locale.ROOT) in ignored
            val text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(post.author.displayName); append("：") }
                append(if (hidden) "内容已忽略" else plainText(post.cooked).ifBlank { "[图片或附件]" })
            }
            Text(text, Modifier.fillMaxWidth().clickable(enabled = enabled) { open(post.number) }.testTag("reply-preview-post-${post.number}"),
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (load?.loading == true && thread.replies.isEmpty()) "正在加载回复…" else if (hasMore || thread.replies.size > 3) "查看更多回复" else "查看 ${thread.replies.size} 条回复",
            Modifier.fillMaxWidth().clickable(enabled = enabled) { open(null) }.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
}
