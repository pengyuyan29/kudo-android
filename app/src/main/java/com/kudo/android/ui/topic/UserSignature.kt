package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kudo.android.data.Post
import com.kudo.android.data.plainText
import com.kudo.android.ui.components.AvatarImage
import com.kudo.android.ui.components.mediaUrl
import com.kudo.android.ui.theme.LocalDisplaySettings

internal fun signatureBlocks(value: String): List<ArticleBlock> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (!trimmed.contains('<')) mediaUrl(trimmed)?.takeIf {
        trimmed.startsWith("https://") || trimmed.startsWith("//") || trimmed.startsWith('/')
    }?.let { return listOf(ArticleBlock.Image(it, "用户签名图片")) }
    return parseArticle(trimmed)
}

@Composable
internal fun UserSignature(post: Post, openLink: (String) -> Unit) {
    if (!LocalDisplaySettings.current.showSignatures || post.signature.isBlank()) return
    val blocks = remember(post.signature) { signatureBlocks(post.signature) }
    if (blocks.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("user-signature-${post.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        blocks.forEach { block -> when (block) {
            is ArticleBlock.Image -> BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 120.dp)
                .aspectRatio(block.ratio ?: 3.8f).clip(RoundedCornerShape(6.dp))
                .semantics { contentDescription = "用户签名图片" }) {
                val pixels = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
                AvatarImage(block.url, pixels, true, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    placeholder = { Text("签名图片", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) })
            }
            is ArticleBlock.TextBlock -> {
                val color = MaterialTheme.colorScheme.primary
                val article = remember(block.html, color) { parseInlineArticle(block.html, color) }
                CompositionLocalProvider(LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = openLink(uri) }) {
                Text(article.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth())
                }
            }
            is ArticleBlock.Embed -> Text(block.title, Modifier.clickable { openLink(block.url) }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            is ArticleBlock.Code -> Text(block.text, style = MaterialTheme.typography.bodySmall)
            ArticleBlock.Rule -> Unit
        } }
    }
}
