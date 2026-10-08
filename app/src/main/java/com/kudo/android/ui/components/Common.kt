package com.kudo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kudo.android.data.*
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow
import com.kudo.android.ui.topic.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

fun mediaUrl(value: String): String? {
    val url = "https://linux.do/".toHttpUrlOrNull()?.resolve(value.replace("{size}", "120")) ?: return null
    return url.takeIf { it.isHttps && value.isNotBlank() }?.toString()
}

@Composable
fun PageTopBar(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().heightIn(min = 58.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: ImageVector = Icons.Rounded.Forum, actionLabel: String? = null, action: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(68.dp).background(LogoYellow.copy(alpha = 0.12f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(31.dp), MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null) Button(onClick = action, colors = ButtonDefaults.buttonColors(containerColor = LogoYellow, contentColor = Ink)) { Text(actionLabel) }
    }
}

@Composable
fun ErrorState(error: Throwable, retry: () -> Unit, login: () -> Unit) {
    val kind = (error as? ApiFailure)?.kind
    val auth = kind == FailureKind.SIGN_IN || kind == FailureKind.VERIFICATION
    EmptyState(if (kind == FailureKind.VERIFICATION) "和社区连接，只差一步" else if (kind == FailureKind.SIGN_IN) "登录后继续" else "暂时没能加载",
        error.message ?: "请稍后重试", if (auth) Icons.Rounded.VerifiedUser else Icons.Rounded.CloudOff,
        if (kind == FailureKind.VERIFICATION) "完成网页验证" else if (auth) "登录 Linux.do" else "重新加载", if (auth) login else retry)
}

@Composable
fun LoadingRow() { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = LogoYellow) } }

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun TopicCard(topic: Topic, category: Category?, onClick: () -> Unit, onUser: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(topic.author, 35.dp) { topic.author?.username?.let(onUser) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(topic.author?.displayName ?: "Linux.do 社区", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(relativeTime(topic.bumpedAt).ifBlank { "社区话题" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (topic.pinned) Icon(Icons.Rounded.PushPin, "置顶", Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
            else Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(19.dp), MaterialTheme.colorScheme.outline)
        }
        Text(topic.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (topic.excerpt.isNotBlank()) Text(topic.excerpt, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        mediaUrl(topic.image)?.let { TopicPreviewImage(topic.id, it, onClick) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            category?.let { Text(it.name, Modifier.clip(RoundedCornerShape(5.dp)).background(LogoYellow.copy(alpha = 0.13f)).padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
            topic.tags.take(2).forEach { Text("# $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(Icons.Rounded.Visibility, compactNumber(topic.views))
            Stat(Icons.Rounded.ChatBubbleOutline, compactNumber(topic.replies))
            Stat(Icons.Rounded.ThumbUpOffAlt, compactNumber(topic.likes))
        }
    }
}

@Composable
private fun TopicPreviewImage(topicId: Int, url: String, openTopic: () -> Unit) {
    val gallery = LocalImageGallery.current
    val origin = rememberSaveable { UUID.randomUUID().toString() }
    var preview by rememberSaveable(topicId, url, stateSaver = GalleryImageSaver) { mutableStateOf(ArticleBlock.Image(url, "话题图片")) }
    val anchor = remember { GalleryThumbnailAnchor().apply { contentScale = ContentScale.Crop } }
    val radius = with(LocalDensity.current) { 10.dp.toPx() }
    SideEffect { anchor.cornerRadius = radius }
    DisposableEffect(anchor) { onDispose { anchor.coordinates = null; anchor.painter = null } }
    RegisterGalleryOrigin(origin) { image, _ ->
        preview = image
        withFrameNanos { }
        anchor.requester.bringIntoView()
        withFrameNanos { }
        anchor
    }
    AsyncImage(preview.url, "话题图片，点击放大", Modifier.fillMaxWidth().heightIn(max = 190.dp).aspectRatio(1.9f)
        .bringIntoViewRequester(anchor.requester).onGloballyPositioned { anchor.coordinates = it }
        .testTag("topic-image-$topicId").clip(RoundedCornerShape(10.dp)).clickable {
            if (gallery == null) openTopic() else gallery.open(listOf(preview), 0, origin, anchor, topicId)
        }, contentScale = ContentScale.Crop, onSuccess = { anchor.painter = it.painter })
}

@Composable
fun Stat(icon: ImageVector, text: String) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Icon(icon, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurfaceVariant); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
