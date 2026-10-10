package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.components.Avatar
import com.kudo.android.ui.components.TopicAuthorMetadata
import com.kudo.android.ui.components.LocalTopicPresentation
import com.kudo.android.ui.components.liquid.LiquidButton
import com.kudo.android.ui.theme.LogoYellow
import com.kyant.backdrop.Backdrop

@Composable
internal fun TopicTopBar(
    state: TopicState, enabled: Boolean, back: () -> Unit, user: (String) -> Unit,
    follow: () -> Unit, more: () -> Unit,
) {
    val author = state.detail?.author
    val profile = state.authorProfile
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()
        .heightIn(min = 52.dp).testTag("topic-top-bar"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back, enabled = enabled, modifier = Modifier.width(56.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
        }
        if (author == null) Text("话题详情", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        else Row(Modifier.weight(1f).heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button) { user(author.username) }.testTag("topic-author"),
            verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, 32.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                Text(author.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                TopicAuthorMetadata(state.detail.originalPost?.updatedAt ?: state.detail.createdAt,
                    LocalTopicPresentation.current.categories.firstOrNull { it.id == state.detail.categoryId },
                    state.detail.originalPost?.edited == true)
            }
        }
        if (author != null && author.id != state.viewerId &&
            (state.viewerId == null || profile == null || profile.canFollow || profile.isFollowed)) {
            val canClick = enabled && !state.loading && !state.followBusy && (state.viewerId == null || !state.authorLoading)
            Box(Modifier.heightIn(min = 48.dp).widthIn(min = 56.dp)
                .clickable(enabled = canClick, role = Role.Button, onClick = follow)
                .testTag("topic-follow"), contentAlignment = Alignment.Center) {
                Text(if (state.authorError != null && state.viewerId != null) "重试" else if (profile?.isFollowed == true) "已关注" else "关注",
                    Modifier.border(1.dp, if (canClick) LogoYellow else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal,
                    color = if (canClick) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.detail?.originalPost?.let { PostUnreadIndicator(it, state) }
        IconButton(onClick = more, enabled = enabled && state.detail != null) { Icon(Icons.Rounded.MoreVert, "更多") }
    }
}

@Composable
internal fun TopicReactionButton(
    post: Post?, enabled: Boolean, choose: (() -> Unit)?, click: () -> Unit,
    modifier: Modifier = Modifier, bottom: Boolean = false,
) {
    val reaction = post?.activeReaction
    val label = if (bottom) {
        if (reaction != null && reaction != "heart") "取消主帖回应" else "赞主帖"
    } else "回应此楼层"
    Row(modifier.clip(RoundedCornerShape(50))
        .combinedClickable(enabled = enabled, role = Role.Button,
            onClickLabel = if (reaction == null) "点赞" else "取消回应", onClick = click,
            onLongClickLabel = "选择回应", onLongClick = choose)
        .heightIn(min = 48.dp).widthIn(min = 48.dp)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            stateDescription = when {
                reaction == null -> "未回应"
                post?.canReact == false -> "已回应 $reaction，无法撤销"
                reaction == "heart" -> "已点赞，短按取消"
                else -> "已回应 $reaction，短按取消"
            }
        }.padding(horizontal = if (bottom) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val size = if (bottom) 22.dp else 19.dp
        if (reaction != null && reaction != "heart") SiteEmoji(reaction, Modifier.size(size))
        else Icon(if (reaction == "heart") Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
            Modifier.size(size), tint = if (reaction == "heart") LogoYellow else LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f))
        if (!bottom) {
            Spacer(Modifier.width(5.dp))
            Text(post?.reactionCount?.toString().orEmpty(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun TopicBottomBar(
    state: TopicState, backdrop: Backdrop, glass: GlassMode, enabled: Boolean,
    reply: () -> Unit, like: (Post) -> Unit, bookmark: (Post) -> Unit, share: () -> Unit,
    choose: (Post) -> Unit, modifier: Modifier = Modifier,
) {
    val detail = state.detail ?: return
    val first = detail.originalPost
    val available = enabled && !state.loading && first != null && first.id !in state.busy
    Row(modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 20.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LiquidButton(onClick = reply, enabled = enabled && !detail.closed, backdrop = backdrop, mode = glass,
            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            contentColor = MaterialTheme.colorScheme.onSurface, contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
            modifier = Modifier.testTag("topic-reply")) {
            Icon(Icons.Outlined.DriveFileRenameOutline, null, Modifier.size(20.dp).testTag("topic-reply-icon"))
            Text("写回复", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        LiquidButton(onClick = null, backdrop = backdrop, mode = glass,
            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.testTag("topic-actions")) {
            TopicReactionButton(first, available && (state.viewerId == null || first?.canReact == true),
                choose = if (first?.reactionsAvailable == true && detail.validReactions.isNotEmpty()) ({ choose(first) }) else null,
                click = { first?.let(like) }, bottom = true)
            IconButton(onClick = { first?.let(bookmark) }, enabled = available) {
                Icon(if (first?.bookmarkId != null) Icons.Rounded.Star else Icons.Rounded.StarOutline, "收藏主帖", Modifier.size(22.dp),
                    tint = if (first?.bookmarkId != null) LogoYellow else LocalContentColor.current)
            }
            IconButton(onClick = share, enabled = enabled) { Icon(Icons.Rounded.Share, "分享话题", Modifier.size(22.dp)) }
        }
    }
}

@Composable
internal fun TopicReactionChoices(post: Post, choices: List<String>, enabled: Boolean, select: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { reaction ->
            val selected = post.activeReaction == reaction
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .background(if (selected) LogoYellow.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                .clickable(enabled = enabled, role = Role.Button) { select(reaction) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "回应 $reaction"
                    stateDescription = if (selected) "已选择，短按取消" else "未选择"
                }.testTag("reaction-choice-$reaction"), contentAlignment = Alignment.Center) {
                SiteEmoji(reaction, Modifier.size(28.dp))
            }
        }
    }
}
