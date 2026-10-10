package com.kudo.android.ui.topic

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.kudo.android.data.AvatarMetadataCache
import com.kudo.android.ui.components.AvatarImage
import com.kudo.android.ui.components.ImageAspectBadge
import com.kudo.android.ui.components.LocalAvatarMetadata
import com.kudo.android.ui.components.mediaUrl
import com.kudo.android.ui.LinuxDoLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.UUID

sealed interface ArticleBlock {
    data class TextBlock(val html: String, val quote: Boolean = false, val heading: Boolean = false) : ArticleBlock
    data class Quote(val blocks: List<ArticleBlock>, val author: String = "", val avatar: String? = null, val source: String? = null) : ArticleBlock
    data class Details(val summary: String, val blocks: List<ArticleBlock>, val open: Boolean = false) : ArticleBlock
    data class Code(val text: String) : ArticleBlock
    data class Image(
        val url: String, val alt: String, val ratio: Float? = null,
        val originalUrl: String = url, val filename: String = "",
        val dimensions: String = "", val sizeLabel: String = "",
    ) : ArticleBlock
    data class Embed(val url: String, val title: String, val description: String = "", val image: String? = null, val site: String = "") : ArticleBlock
    data object Rule : ArticleBlock
}

private fun previewSource(element: Element, fallback: String): String {
    val width = element.attr("width").toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
    var bestUrl = fallback
    var bestDensity = 1f
    element.attr("srcset").split(',').forEach { candidate ->
        val parts = candidate.trim().split(Regex("\\s+"))
        if (parts.size !in 1..2) return@forEach
        val url = mediaUrl(parts[0]) ?: return@forEach
        val descriptor = parts.getOrNull(1)
        val density = when {
            descriptor == null -> 1f
            descriptor.endsWith('x') -> descriptor.dropLast(1).toFloatOrNull()
            descriptor.endsWith('w') && width != null -> descriptor.dropLast(1).toIntOrNull()?.div(width)
            else -> null
        } ?: return@forEach
        if (density.isFinite() && density > bestDensity) {
            bestUrl = url
            bestDensity = density
        }
    }
    return bestUrl
}

fun parseArticle(html: String): List<ArticleBlock> {
    val result = mutableListOf<ArticleBlock>()
    fun walk(element: Element, quote: Boolean = false) {
        if (element.hasClass("meta") && element.parents().any { it.hasClass("lightbox") || it.hasClass("lightbox-wrapper") }) return
        when (element.normalName()) {
            "script", "style", "noscript" -> Unit
            "pre" -> result.add(ArticleBlock.Code(element.wholeText()))
            "table" -> result.add(ArticleBlock.Code(element.select("tr").joinToString("\n") { row -> row.select("th,td").joinToString("  │  ") { it.text() } }))
            "hr" -> result.add(ArticleBlock.Rule)
            "details" -> {
                val summary = element.children().firstOrNull { it.normalName() == "summary" }
                val body = element.clone().apply { children().firstOrNull { it.normalName() == "summary" }?.remove() }
                result.add(ArticleBlock.Details(summary?.html()?.takeIf { summary.text().isNotBlank() } ?: "详细内容",
                    parseArticle(body.html()), element.hasAttr("open")))
            }
            "img" -> {
                val raw = element.attr("src")
                if (element.hasClass("emoji")) result.add(ArticleBlock.TextBlock(element.outerHtml(), quote))
                else mediaUrl(raw)?.let { url ->
                    val lightbox = element.parents().firstOrNull { it.hasClass("lightbox") }
                    val metadata = lightbox?.selectFirst(".meta")
                    val information = metadata?.selectFirst(".informations")?.text().orEmpty()
                    val dimensions = Regex("\\d+\\s*[×x]\\s*\\d+").find(information)?.value.orEmpty()
                    val width = element.attr("width").toFloatOrNull()
                    val height = element.attr("height").toFloatOrNull()
                    val ratio = if (width != null && height != null && width > 0 && height > 0) {
                        (width / height).takeIf { it.isFinite() && it > 0 }
                    } else null
                    result.add(ArticleBlock.Image(previewSource(element, url), element.attr("alt"), ratio,
                        originalUrl = lightbox?.attr("href")?.let(::mediaUrl) ?: url,
                        filename = metadata?.selectFirst(".filename")?.text().orEmpty(),
                        dimensions = dimensions, sizeLabel = information.removePrefix(dimensions).trim()))
                }
            }
            "iframe", "video", "audio" -> {
                val raw = element.attr("src").ifBlank { element.selectFirst("source")?.attr("src").orEmpty() }
                LinuxDoLink.webUrl(raw)?.let { result.add(ArticleBlock.Embed(it, "打开媒体网页")) }
            }
            "blockquote", "aside" -> {
                if (element.hasClass("onebox")) {
                    parseOnebox(element)?.let(result::add)
                } else {
                    val siteQuote = element.normalName() == "aside" && element.hasClass("quote")
                    val title = if (siteQuote) element.children().firstOrNull { it.hasClass("title") } else null
                    val author = if (siteQuote) element.attr("data-display-name").ifBlank {
                        title?.clone()?.apply { select("img, .quote-controls").remove() }?.text()?.trim()?.removeSuffix(":").orEmpty()
                    }.ifBlank { element.attr("data-username") } else ""
                    val topic = element.attr("data-topic").toIntOrNull()?.takeIf { it > 0 }
                    val post = element.attr("data-post").toIntOrNull()?.takeIf { it > 0 }
                    val source = if (siteQuote && topic != null) "https://linux.do/t/topic/$topic" + (post?.let { "/$it" } ?: "") else null
                    val body = element.clone().apply {
                        if (title != null) children().firstOrNull { it.hasClass("title") }?.remove()
                        // The direct blockquote is the site's body wrapper, not a second nested quote.
                        if (siteQuote) children().filter { it.normalName() == "blockquote" }.forEach { it.unwrap() }
                    }
                    val content = parseArticle(body.html()).map { if (it is ArticleBlock.TextBlock) it.copy(quote = true) else it }
                    if (content.isNotEmpty() || author.isNotBlank()) result.add(ArticleBlock.Quote(content, author,
                        title?.selectFirst("img.avatar")?.attr("src")?.let(::mediaUrl), source))
                }
            }
            "p", "h1", "h2", "h3", "h4", "li", "summary" -> {
                val images = element.select("img:not(.emoji)")
                val copy = element.clone().apply { select("img:not(.emoji), .lightbox .meta, .lightbox-wrapper .meta").remove() }
                if (copy.text().isNotBlank() || copy.select("img.emoji").isNotEmpty()) result.add(ArticleBlock.TextBlock(if (element.normalName() == "li") "• ${copy.html()}" else copy.html(), quote, element.normalName().startsWith("h")))
                images.forEach { walk(it, quote) }
            }
            else -> {
                if (element.children().isEmpty() && element.text().isNotBlank()) result.add(ArticleBlock.TextBlock(element.html(), quote))
                else element.childNodes().forEach { node ->
                    if (node is Element) walk(node, quote)
                    else if (node is TextNode && !node.isBlank) result.add(ArticleBlock.TextBlock(node.outerHtml(), quote))
                }
            }
        }
    }
    walk(Jsoup.parseBodyFragment(html).body())
    return result
}

private data class ArticleImageLocation(val image: ArticleBlock.Image, val path: String, val quotes: List<String>)

private fun articleImageLocations(blocks: List<ArticleBlock>): List<ArticleImageLocation> = buildList {
    fun visit(items: List<ArticleBlock>, parent: String, quotes: List<String>) {
        items.forEachIndexed { index, block ->
            val path = "$parent-$index"
            when (block) {
                is ArticleBlock.Image -> add(ArticleImageLocation(block, path, quotes))
                is ArticleBlock.Quote -> visit(block.blocks, path, quotes + path)
                is ArticleBlock.Details -> visit(block.blocks, path, quotes + path)
                else -> Unit
            }
        }
    }
    visit(blocks, "root", emptyList())
}

internal fun List<ArticleBlock>.articleImages(): List<ArticleBlock.Image> = articleImageLocations(this).map { it.image }

private fun closedDetails(blocks: List<ArticleBlock>, parent: String = "root"): List<String> = buildList {
    blocks.forEachIndexed { index, block ->
        val path = "$parent-$index"
        when (block) {
            is ArticleBlock.Details -> { if (!block.open) add(path); addAll(closedDetails(block.blocks, path)) }
            is ArticleBlock.Quote -> addAll(closedDetails(block.blocks, path))
            else -> Unit
        }
    }
}

internal data class InlineEmoji(val url: String, val name: String)
internal data class InlineArticle(val text: AnnotatedString, val emojis: Map<String, InlineEmoji>)

internal fun parseInlineArticle(html: String, linkColor: Color): InlineArticle {
    val emojis = linkedMapOf<String, InlineEmoji>()
    val text = buildAnnotatedString {
        fun appendNode(node: Node) {
            if (node is TextNode) append(node.text())
            else if (node is Element) {
                val children: AnnotatedString.Builder.() -> Unit = { node.childNodes().forEach(::appendNode) }
                when (node.normalName()) {
                    "br" -> append("\n")
                    "strong", "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold), children)
                    "em", "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic), children)
                    "del", "s" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), children)
                    "code" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = 0.1f)), children)
                    "img" -> {
                        val url = mediaUrl(node.attr("src"))
                        if (node.hasClass("emoji") && url != null) {
                            val id = "emoji-${emojis.size}"
                            emojis[id] = InlineEmoji(url, node.attr("alt").ifBlank { node.attr("title") }.ifBlank { "表情" })
                            appendInlineContent(id, "\uFFFC")
                        } else append(node.attr("alt"))
                    }
                    "a" -> {
                        val url = LinuxDoLink.webUrl(node.attr("href"))
                        if (url != null) withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))), children)
                        else children()
                    }
                    "script", "style" -> Unit
                    else -> children()
                }
            }
        }
        Jsoup.parseBodyFragment(html).body().childNodes().forEach(::appendNode)
    }
    return InlineArticle(text, emojis)
}

@Composable
internal fun SiteEmoji(name: String, modifier: Modifier = Modifier, source: String? = null) {
    val metadata = LocalAvatarMetadata.current
    val key = name.removeSurrounding(":")
    var url by remember(key, source, metadata) {
        mutableStateOf(source ?: if (metadata == null) AvatarMetadataCache.standardEmojiUrl(key) else null)
    }
    LaunchedEffect(key, source, metadata) {
        if (source == null) url = metadata?.emojiUrl(key) ?: AvatarMetadataCache.standardEmojiUrl(key)
    }
    val pixels = with(LocalDensity.current) { (24.dp.toPx() * fontScale).toInt().coerceAtLeast(1) }
    AvatarImage(url?.let(::mediaUrl), pixels, animate = true,
        modifier = modifier.size(24.dp).semantics { contentDescription = name }, contentScale = ContentScale.Fit) {
        Icon(Icons.Rounded.Mood, null, Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RichContent(html: String, onImage: (String) -> Unit, onLink: (String) -> Unit,
                galleryKey: String = "article", galleryBottomInset: Dp = 0.dp) {
    val blocks = remember(html) { parseArticle(html) }
    val linkHandler = remember(onLink) { object : UriHandler {
        override fun openUri(uri: String) = onLink(uri)
    } }
    val locations = remember(blocks) { articleImageLocations(blocks) }
    val imageIndices = remember(locations) { locations.mapIndexed { index, location -> location.path to index }.toMap() }
    val images = remember(locations) { locations.map { it.image } }
    val anchors = remember(blocks) { List(images.size) { GalleryThumbnailAnchor() } }
    // listSaver drops empty lists; an expanded article must also survive restoration.
    var collapsed by rememberSaveable(html, stateSaver = Saver<List<String>, ArrayList<String>>(
        save = { ArrayList(it) }, restore = { it.toList() })) {
        mutableStateOf(closedDetails(blocks))
    }
    val gallery = LocalImageGallery.current
    val origin = rememberSaveable { UUID.randomUUID().toString() }
    val bottomInset = with(LocalDensity.current) { galleryBottomInset.toPx() }
    RegisterGalleryOrigin(origin) { image, index ->
        anchors.getOrNull(index)?.takeIf { images.getOrNull(index)?.let { sameGalleryImage(it, image) } == true }?.also {
            val ancestors = locations[index].quotes
            if (ancestors.any { path -> path in collapsed }) {
                collapsed = collapsed.filterNot { path -> path in ancestors }
                withFrameNanos { }
                while (it.coordinates?.isAttached != true) withFrameNanos { }
            }
            val size = it.coordinates?.takeIf { layout -> layout.isAttached }?.size
            it.requester.bringIntoView(size?.let { bounds -> Rect(0f, 0f, bounds.width.toFloat(), bounds.height + bottomInset) })
            withFrameNanos { }
        }
    }
    ArticleBlocks(blocks, "root", collapsed, { path ->
        collapsed = if (path in collapsed) collapsed - path else collapsed + path
    }, onLink, linkHandler) { block, path ->
        val index = imageIndices.getValue(path)
        ArticleImage(block, anchors[index], "article-image-$galleryKey-$index") {
            if (gallery == null) onImage(block.originalUrl)
            else gallery.open(images, index, origin, anchors[index])
        }
    }
}

@Composable
private fun ArticleBlocks(blocks: List<ArticleBlock>, parent: String, collapsed: List<String>, toggle: (String) -> Unit,
    onLink: (String) -> Unit, linkHandler: UriHandler, image: @Composable (ArticleBlock.Image, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { index, block ->
            val path = "$parent-$index"
            key(path) { when (block) {
            is ArticleBlock.Details -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).testTag("article-details-$path")) {
                val hidden = path in collapsed
                val color = MaterialTheme.colorScheme.primary
                val summary = remember(block.summary, color) { parseInlineArticle(block.summary, color).text }
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { toggle(path) }
                    .semantics { contentDescription = if (hidden) "展开折叠内容" else "收起折叠内容" }
                    .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (hidden) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                if (!hidden) Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    ArticleBlocks(block.blocks, path, collapsed, toggle, onLink, linkHandler, image)
                }
            }
            is ArticleBlock.Quote -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp).testTag("article-quote-$path"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (block.author.isNotBlank() || block.source != null) {
                    val hidden = path in collapsed
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 36.dp).clickable(role = Role.Button) { toggle(path) }
                            .semantics { contentDescription = if (hidden) "展开引用" else "收起引用" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            block.avatar?.let { url -> AsyncImage(url, "引用作者头像", Modifier.size(20.dp)
                                .clip(RoundedCornerShape(50)).testTag("quote-avatar-$path"), contentScale = ContentScale.Crop) }
                            Text(block.author.ifBlank { "引用" }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(if (hidden) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null,
                                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        block.source?.let { source -> Box(Modifier.size(36.dp).clickable(role = Role.Button) { onLink(source) }
                            .semantics { contentDescription = "跳转引用原帖" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.NorthEast, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                    }
                }
                if (path !in collapsed) ArticleBlocks(block.blocks, path, collapsed, toggle, onLink, linkHandler, image)
            }
            is ArticleBlock.TextBlock -> {
                val color = MaterialTheme.colorScheme.primary
                val article = remember(block.html, color) { parseInlineArticle(block.html, color) }
                val inline = remember(article.emojis) {
                    article.emojis.mapValues { (_, emoji) ->
                        InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
                            SiteEmoji(emoji.name, Modifier.fillMaxSize(), emoji.url)
                        }
                    }
                }
                CompositionLocalProvider(LocalUriHandler provides linkHandler) {
                    SelectionContainer {
                        Text(article.text, Modifier.fillMaxWidth(), inlineContent = inline, style = if (block.heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = if (block.quote) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            is ArticleBlock.Code -> SelectionContainer {
                Text(block.text, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).horizontalScroll(rememberScrollState()).padding(13.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
            is ArticleBlock.Image -> image(block, path)
            is ArticleBlock.Embed -> OneboxPreview(block, onLink)
            ArticleBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        } } }
    }
}

@Composable
private fun ArticleImage(image: ArticleBlock.Image, anchor: GalleryThumbnailAnchor, tag: String, open: () -> Unit) {
    val gallery = LocalImageGallery.current
    var decodedRatio by remember(image.url) { mutableStateOf<Float?>(null) }
    val cornerRadius = with(LocalDensity.current) { 11.dp.toPx() }
    SideEffect { anchor.cornerRadius = cornerRadius }
    DisposableEffect(anchor) { onDispose { anchor.coordinates = null; anchor.painter = null } }
    Box(
        Modifier.heightIn(max = 300.dp).aspectRatio(decodedRatio ?: image.ratio ?: 1.6f)
            .bringIntoViewRequester(anchor.requester).onGloballyPositioned { anchor.coordinates = it }.testTag(tag)
            .graphicsLayer { alpha = if (gallery?.hiddenAnchor === anchor) 0f else 1f }
            .clip(RoundedCornerShape(11.dp)).clickable(onClick = open)) {
        AsyncImage(image.url, image.alt.ifBlank { "帖子图片，点击放大" }, Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val bitmap = state.result.image
                if (bitmap.width > 0 && bitmap.height > 0) decodedRatio = bitmap.width.toFloat() / bitmap.height
                anchor.painter = state.painter
            })
        ImageAspectBadge(decodedRatio ?: image.ratio)
    }
}
