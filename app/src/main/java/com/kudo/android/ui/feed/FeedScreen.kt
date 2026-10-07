package com.kudo.android.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.R
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.LogoYellow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(state: FeedState, user: Member?, select: (String) -> Unit, refresh: () -> Unit, more: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit, search: () -> Unit, login: () -> Unit, categories: () -> Unit, modifier: Modifier = Modifier, categoryTitle: String? = null, back: () -> Unit = {}) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).then(if (categoryTitle == null) Modifier.statusBarsPadding().nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)) {
        if (categoryTitle != null) PageTopBar(categoryTitle, back)
        else Row(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
            val offset = scrollBehavior.state.heightOffset.roundToInt()
            layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) { placeable.placeRelative(0, offset) }
        }.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = login), contentAlignment = Alignment.Center) {
                if (user == null) Image(painterResource(R.drawable.ic_linux_do), "Linux.do 社区 Logo", Modifier.size(36.dp))
                else Avatar(user, 36.dp)
            }
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = search), contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("真诚 · 友善 · 团结 · 专业", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.Search, "搜索", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val tabs = FeedFilter.available(user != null, categoryTitle != null).map {
            it.value to if (it == FeedFilter.NEW && state.newCount != null) "新 (${state.newCount})" else it.label
        } + if (categoryTitle == null) listOf("categories" to "类别") else emptyList()
        key(user?.id) { FeedTabBar(tabs, state.filter) { if (it == "categories") categories() else select(it) } }
        val listState = rememberLazyListState()
        LaunchedEffect(state.filter, state.accountId) { listState.scrollToItem(0) }
        val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; info.totalItemsCount > 3 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 } }
        LaunchedEffect(nearEnd, state.next) { if (nearEnd && state.next != null && state.moreError == null) more() }
        PullToRefreshBox(isRefreshing = state.loading && state.topics.isNotEmpty(), onRefresh = refresh, modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                if (state.loading && state.topics.isEmpty()) item { LoadingRow() }
                if (state.error != null) item { ErrorState(state.error, refresh, login) }
                if (!state.loading && state.error == null && state.topics.isEmpty()) item { EmptyState("暂时没有话题", "换个分类，或者稍后再来看看。") }
                items(state.topics, key = { "topic-${it.id}" }) { topic -> TopicCard(topic, state.categories.firstOrNull { it.id == topic.categoryId }, { openTopic(topic.id) }, openUser) }
                if (state.loadingMore) item { LoadingRow() }
                else if (state.moreError != null) item { ErrorState(state.moreError, more, login) }
                else if (state.next != null) item { TextButton(onClick = more, modifier = Modifier.fillMaxWidth()) { Text("加载更多话题") } }
                else if (state.topics.isNotEmpty()) item { Text("已经看到这里的全部话题", Modifier.fillMaxWidth().padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun FeedTabBar(tabs: List<Pair<String, String>>, selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { (value, label) ->
            key(value) {
                val active = selected == value
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(active) { if (active) bringIntoView.bringIntoView() }
                Box(Modifier.bringIntoViewRequester(bringIntoView).selectable(active, role = Role.Tab, onClick = { select(value) }).heightIn(min = 40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(label, Modifier.padding(vertical = 7.dp), fontSize = if (active) 19.sp else 16.sp, lineHeight = if (active) 26.sp else 22.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(22.dp).height(3.dp).background(if (active) LogoYellow else Color.Transparent, CircleShape))
                }
            }
        }
    }
}
