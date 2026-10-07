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

@Composable
fun SearchScreen(state: SearchState, search: (String, Boolean) -> Unit, back: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit, login: () -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageTopBar("搜索社区", back)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("话题、技术、感兴趣的事…") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { TextButton(onClick = { keyboard?.hide(); search(query, false) }, enabled = query.isNotBlank()) { Text("搜索") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); search(query, false) }))
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.searched) item { EmptyState("好内容，值得被找到", "支持 Discourse 搜索语法，例如 category:开发调优。", Icons.Rounded.Search) }
            items(state.results, key = { it.id }) { topic -> TopicCard(topic, null, { openTopic(topic.id) }, openUser) }
            if (state.loading) item { LoadingRow() }
            if (state.error != null) item { ErrorState(state.error, { search(state.query, false) }, login) }
            if (state.searched && !state.loading && state.results.isEmpty() && state.error == null) item { EmptyState("没有找到相关话题", "试试更短的关键词，或更换搜索条件。", Icons.Rounded.Search) }
            if (state.hasMore && !state.loading) item { TextButton(onClick = { search(state.query, true) }, modifier = Modifier.fillMaxWidth()) { Text("更多搜索结果") } }
        }
    }
}
