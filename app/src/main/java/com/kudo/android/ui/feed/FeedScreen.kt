package com.kudo.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

val FeedFilters = listOf("latest" to "最新", "hot" to "热门", "top" to "精华", "new" to "新帖", "unread" to "未读")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(state: FeedState, user: Member?, select: (String) -> Unit, refresh: () -> Unit, more: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit, search: () -> Unit, login: () -> Unit, categories: () -> Unit, modifier: Modifier = Modifier, categoryTitle: String? = null, back: () -> Unit = {}) {
    Column(modifier.fillMaxSize()) {
        if (categoryTitle != null) PageTopBar(categoryTitle, back)
        else Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = login), contentAlignment = Alignment.Center) { Avatar(user, 36.dp) }
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = search), contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索 Linux.do 的精彩", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.Search, "搜索", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("酷DO", fontWeight = FontWeight.Black, fontSize = 18.sp, lineHeight = 24.sp)
        }
        ScrollableTabRow(selectedTabIndex = FeedFilters.indexOfFirst { it.first == state.filter }.coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary, edgePadding = 10.dp, divider = {}, indicator = {}) {
            FeedFilters.forEach { (value, label) ->
                Tab(state.filter == value, { select(value) }, text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontSize = if (state.filter == value) 19.sp else 16.sp, lineHeight = if (state.filter == value) 26.sp else 22.sp, fontWeight = if (state.filter == value) FontWeight.Bold else FontWeight.Normal, color = if (state.filter == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(22.dp).height(3.dp).background(if (state.filter == value) LogoYellow else Color.Transparent, CircleShape))
                    }
                })
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(state.filter) { listState.scrollToItem(0) }
        val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; info.totalItemsCount > 3 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 } }
        LaunchedEffect(nearEnd, state.next) { if (nearEnd && state.next != null && state.moreError == null) more() }
        PullToRefreshBox(isRefreshing = state.loading && state.topics.isNotEmpty(), onRefresh = refresh, modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                if (categoryTitle == null) {
                    item("welcome") { CommunityBanner() }
                    item("shortcuts") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf(Triple("新鲜事", Icons.Rounded.Bolt, "latest"), Triple("热议中", Icons.Rounded.LocalFireDepartment, "hot"), Triple("值得看", Icons.Rounded.AutoAwesome, "top"), Triple("逛分类", Icons.Rounded.Widgets, "categories")).forEach { (label, icon, action) ->
                                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { if (action == "categories") categories() else select(action) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(36.dp).background(LogoYellow.copy(alpha = if (action == "hot") 0.25f else 0.13f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
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
fun CommunityBanner() {
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFFE8A6), Color(0xFFFFCB52)))).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LINUX.DO", color = Ink.copy(alpha = 0.65f), letterSpacing = 2.sp, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
            Text("认真交流，自在分享", color = Ink, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            Text("真诚 · 友善 · 团结 · 专业", color = Color(0xFF76571C), fontSize = 12.sp, lineHeight = 18.sp)
        }
        Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text("DO", color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}
