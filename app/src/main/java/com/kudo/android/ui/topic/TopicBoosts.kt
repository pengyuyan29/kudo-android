package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import com.kudo.android.data.*
import com.kudo.android.ui.components.*

@Composable
internal fun BoostForm(raw: String, change: (String) -> Unit, state: TopicState, sending: Boolean,
    send: () -> Unit, close: () -> Unit, retry: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val names = state.boostEmojis
    val count = remember(raw, names) { BoostRules.count(raw, names.orEmpty()) }
    val ready = names != null && count.problem == null && !sending
    LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
    Column(Modifier.fillMaxWidth().testTag("boost-form"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Boost", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            CommunityIconButton(Icons.Rounded.Close, "关闭 Boost", close, enabled = !sending)
        }
        CommunityTextField(raw, change, Modifier.fillMaxWidth().heightIn(min = 80.dp).focusRequester(focus).testTag("boost-input"),
            placeholder = "用一句话为帖子加油", enabled = !sending, maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (ready) send() }))
        if (state.boostRulesError != null) {
            Text(state.boostRulesError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = retry) { Text("重试") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (names == null) "正在读取表情规则…" else "${count.length} / 16 字 · ${count.emojis} / 5 表情",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (count.length > 16 || count.emojis > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            CommunityButton(if (sending) "发送中…" else "发送", send, enabled = ready, compact = true)
        }
    }
}

@Composable
internal fun PostBoosts(post: Post, enabled: Boolean, user: (String) -> Unit, openLink: (String) -> Unit) {
    if (post.boosts.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val handler = remember(openLink) { object : UriHandler { override fun openUri(uri: String) = openLink(uri) } }
    CompositionLocalProvider(LocalUriHandler provides handler) {
        FlowRow(Modifier.fillMaxWidth().testTag("post-boosts-${post.number}"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            post.boosts.forEach { boost ->
                val article = remember(boost.cooked, color) { parseInlineArticle(boost.cooked, color) }
                val emojis = article.emojis.mapValues { (_, emoji) ->
                    InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
                        SiteEmoji(emoji.name, Modifier.fillMaxSize(), emoji.url)
                    }
                }
                Row(Modifier.heightIn(min = 40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), RoundedCornerShape(20.dp))
                    .clickable(enabled = enabled, role = Role.Button) { user(boost.author.username) }
                    .padding(horizontal = 8.dp, vertical = 7.dp).testTag("boost-${boost.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Avatar(boost.author, 22.dp)
                    Text(article.text, Modifier.weight(1f, fill = false), inlineContent = emojis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
