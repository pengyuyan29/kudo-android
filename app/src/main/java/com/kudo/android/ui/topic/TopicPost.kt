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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import java.util.Locale

@Composable
internal fun TopicPost(post: Post, detail: TopicDetail, state: TopicState, enabled: Boolean,
    like: (Post) -> Unit, bookmark: (Post) -> Unit, reply: (Post?) -> Unit,
    user: (String) -> Unit, image: (String) -> Unit, openLink: (String) -> Unit, choose: (Post) -> Unit,
    originalRevealed: Boolean = false, revealOriginal: () -> Unit = {}, boost: (Post) -> Unit = {},
    vote: (Post, String, List<String>) -> Unit = { _, _, _ -> }, login: () -> Unit = {},
    onHeading: ((ArticleHeading, BringIntoViewRequester) -> Unit)? = null) {
    val ignoredUsers = LocalIgnoredUsers.current
    var revealed by remember(post.id, ignoredUsers) { mutableStateOf(false) }
    val ignored = post.author.username.lowercase(Locale.ROOT) in ignoredUsers
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag("post-${post.number}")) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ignored && !(if (post.number == 1) originalRevealed else revealed)) {
            Text("@${post.author.username} 的内容已忽略", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { if (post.number == 1) revealOriginal() else revealed = true }) { Text("查看此楼层") }
        } else {
            if (post.number != 1) Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(enabled = enabled) { user(post.author.username) }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(post.author, 32.dp, presentation = AvatarPresentation.PUBLIC)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(post.author.displayName, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            MemberNameDecorations(post.author)
                            if (post.author.id == detail.author?.id) Text("楼主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                        }
                        TopicAuthorMetadata(post.updatedAt, detail.categoryId.takeIf { post.number == 1 }?.let { categoryId ->
                            LocalTopicPresentation.current.categories.firstOrNull { it.id == categoryId }
                        }, post.edited)
                    }
                }
                PostUnreadIndicator(post, state)
                val actions = LocalTopicActions.current
                IconButton(onClick = { actions.openReply(detail.id, post, bookmark) }, enabled = enabled && !state.loading && post.id !in state.busy) {
                    Icon(Icons.Rounded.MoreHoriz, "更多操作", Modifier.size(20.dp))
                }
                }
            }
            PostReadingContent(post.number, enabled && !state.loading) {
                CompositionLocalProvider(LocalArticleHeadingCollector provides onHeading) {
                RichContent(post.cooked, image, openLink, galleryKey = "post-${post.id}", galleryBottomInset = 110.dp,
                    poll = { name ->
                        val poll = post.polls.firstOrNull { it.name == name }
                        val web = { openLink("https://linux.do/t/topic/${detail.id}/${post.number}") }
                        if (poll != null) PostPollView(poll, post.pollVotes[name].orEmpty(), post.author.id == state.viewerId,
                            detail.archived || !enabled, post.id in state.busy, state.viewerId != null,
                            { vote(post, name, it) }, login, web)
                        else Text("打开网页查看投票", Modifier.clickable(onClick = web), color = MaterialTheme.colorScheme.primary)
                    })
                }
            }
            UserSignature(post, openLink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TopicReactionButton(post, enabled && !state.loading && post.id !in state.busy && (state.viewerId == null || post.canReact),
                    choose = if (post.reactionsAvailable && detail.validReactions.isNotEmpty()) ({ choose(post) }) else null,
                    click = { like(post) })
                IconButton(onClick = { boost(post) }, enabled = enabled && !state.loading && post.id !in state.busy && (state.viewerId == null || post.canBoost)) {
                    Icon(CategoryIcons.get("rocket"), "Boost 此帖子", Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f * LocalContentColor.current.alpha))
                }
                TextButton(onClick = { reply(post) }, enabled = enabled && !detail.closed) { Text("回复") }
            }
            PostBoosts(post, enabled, user, openLink)
        }
    }
    if (post.number != 1 && (!ignored || revealed)) {
        Text("#${post.number}", Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp)
            .testTag("post-floor-number-${post.number}"),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
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
            val images = remember(post.cooked) { parseArticle(post.cooked).articleImages() }
            Column(Modifier.fillMaxWidth().testTag("reply-preview-post-${post.number}"),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${post.author.displayName}：", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        modifier = Modifier.clickable(enabled = enabled) { open(post.number) })
                    if (hidden) {
                        Text("内容已忽略", Modifier.weight(1f).clickable(enabled = enabled) { open(post.number) }, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    } else {
                    val linkColor = MaterialTheme.colorScheme.primary
                    val textHtml = remember(post.cooked) {
                        parseArticle(post.cooked).filterIsInstance<ArticleBlock.TextBlock>()
                            .joinToString("<br>") { it.html }
                    }
                    val article = remember(textHtml, linkColor) {
                        parseInlineArticle(textHtml, linkColor)
                    }
                    val inline = remember(article.emojis) { article.emojis.mapValues { (_, emoji) ->
                        androidx.compose.foundation.text.InlineTextContent(
                            androidx.compose.ui.text.Placeholder(1.25.em, 1.25.em,
                                androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter),
                        ) { SiteEmoji(emoji.name, Modifier.fillMaxSize(), emoji.url) }
                    } }
                        if (article.text.isNotBlank()) Text(article.text,
                            Modifier.weight(1f).clickable(enabled = enabled) { open(post.number) },
                            inlineContent = inline, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!hidden && images.isNotEmpty()) Row(Modifier.padding(start = 2.dp)) {
                    ReplyPreviewImages(images, post.id, enabled) { open(post.number) }
                }
            }
        }
        Text(if (load?.loading == true && thread.replies.isEmpty()) "正在加载回复…" else if (hasMore || thread.replies.size > 3) "查看更多回复" else "查看 ${thread.replies.size} 条回复",
            Modifier.fillMaxWidth().clickable(enabled = enabled) { open(null) }.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReplyPreviewImages(images: List<ArticleBlock.Image>, postId: Int, enabled: Boolean, fallback: () -> Unit) {
    val gallery = LocalImageGallery.current
    Row(Modifier.width(if (images.size > 1) 94.dp else 80.dp).clickable(enabled = enabled) {
        if (gallery != null) gallery.open(images, 0, "reply-preview-$postId") else fallback()
    }.testTag("reply-preview-images-${postId}"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(Icons.Rounded.Image, "查看图片", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text("查看图片${if (images.size > 1) "(${images.size})" else ""}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            maxLines = 1)
    }
}

@Composable
internal fun ThreadPreviewPost(post: Post, enabled: Boolean, image: (String) -> Unit,
    openLink: (String) -> Unit, navigate: (Int) -> Unit) {
    val hidden = post.author.username.lowercase(Locale.ROOT) in LocalIgnoredUsers.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("thread-preview-post-${post.number}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(post.author, 24.dp)
            Text(post.author.displayName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("#${post.number} · 跳到帖子", Modifier.clickable(enabled = enabled) { navigate(post.number) }
                .padding(vertical = 8.dp).testTag("thread-jump-${post.number}"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (hidden) Text("内容已忽略", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else RichContent(post.cooked, image, openLink, galleryKey = "thread-preview-${post.id}")
    }
}
