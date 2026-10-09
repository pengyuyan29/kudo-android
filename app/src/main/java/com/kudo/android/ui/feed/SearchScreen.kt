package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kudo.android.ui.components.*
import com.kudo.android.data.TopicListStyle

@Composable
fun SearchScreen(state: SearchState, search: (String, Boolean) -> Unit, back: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit, login: () -> Unit, listStyle: TopicListStyle = TopicListStyle.FEED) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val ignored = com.kudo.android.ui.topic.LocalIgnoredUsers.current
    val visibleResults = state.results.filterNot { it.author?.username?.lowercase(java.util.Locale.ROOT) in ignored }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageTopBar("搜索社区", back)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("话题、技术、感兴趣的事…") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { TextButton(onClick = { keyboard?.hide(); search(query, false) }, enabled = query.isNotBlank()) { Text("搜索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); search(query, false) }))
        LazyColumn(contentPadding = if (listStyle == TopicListStyle.TITLE) PaddingValues(bottom = 32.dp) else PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(if (listStyle == TopicListStyle.TITLE) 0.dp else 10.dp)) {
            if (!state.searched) item { EmptyState("好内容，值得被找到", "支持 Discourse 搜索语法，例如 category:开发调优。", Icons.Rounded.Search) }
            items(visibleResults, key = { it.id }, contentType = { listStyle }) { topic ->
                if (listStyle == TopicListStyle.TITLE) TopicTitleRow(topic, null, { openTopic(topic.id) }, openUser)
                else TopicCard(topic, null, { openTopic(topic.id) }, openUser)
            }
            if (state.loading) item { LoadingRow() }
            if (state.error != null) item { ErrorState(state.error, { search(state.query, false) }, login) }
            if (state.searched && !state.loading && visibleResults.isEmpty() && state.error == null) item { EmptyState("没有找到可显示的话题", "试试更短的关键词，或更换搜索条件。", Icons.Rounded.Search) }
            if (state.hasMore && !state.loading) item { TextButton(onClick = { search(state.query, true) }, modifier = Modifier.fillMaxWidth()) { Text("更多搜索结果") } }
        }
    }
}
