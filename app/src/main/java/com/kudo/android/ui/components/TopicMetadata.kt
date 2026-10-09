package com.kudo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class TopicPresentationMetadata(
    val categories: List<Category> = emptyList(),
    val tagIcons: Map<String, TagIconStyle> = DefaultTagIcons,
)
internal val LocalTopicPresentation = staticCompositionLocalOf { TopicPresentationMetadata() }

@Composable
internal fun TopicPresentationEnvironment(repository: CommunityRepository, accountId: Int?, content: @Composable () -> Unit) {
    val metadata = key(repository, accountId) {
        produceState(TopicPresentationMetadata(), repository, accountId) {
            coroutineScope {
                launch {
                    try {
                        withTimeoutOrNull(8000) { withContext(Dispatchers.IO) { repository.categories() } }
                            ?.let { value = value.copy(categories = it) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Auxiliary metadata never blocks reading. */ }
                }
                launch {
                    try {
                        withTimeoutOrNull(8000) { withContext(Dispatchers.IO) { repository.tagIcons() } }
                            ?.takeIf { it.isNotEmpty() }?.let { value = value.copy(tagIcons = it) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Keep the checked bundled tag catalog. */ }
                }
            }
        }
    }
    CompositionLocalProvider(LocalTopicPresentation provides metadata.value, content = content)
}

@Composable
private fun iconColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor("#${value.removePrefix("#")}"))
}.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun CategoryInline(category: Category) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.testTag("category-inline-${category.id}")) {
        Icon(CategoryIcons.get(category.icon), "分类图标：${category.name}", Modifier.size(12.dp), iconColor(category.color))
        Text(category.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TopicAuthorMetadata(time: String, category: Category?, edited: Boolean = false) {
    val timestamp = relativeTime(time)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        if (timestamp.isNotBlank()) Text(timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        category?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (timestamp.isNotBlank()) MetadataSeparator()
                CategoryInline(it)
            }
        }
        if (edited) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (timestamp.isNotBlank() || category != null) MetadataSeparator()
            Text("已编辑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetadataSeparator() { Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }

@Composable
internal fun TopicTagChip(tag: TopicTag) {
    val style = tag.iconStyle(LocalTopicPresentation.current.tagIcons)
    Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
        .testTag("topic-tag-${tag.name}").padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        style?.let { Icon(CategoryIcons.get(it.icon), "标签图标：${tag.name}", Modifier.size(13.dp), iconColor(it.color)) }
        Text(tag.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TopicTags(tags: List<TopicTag>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        tags.forEach { TopicTagChip(it) }
    }
}
