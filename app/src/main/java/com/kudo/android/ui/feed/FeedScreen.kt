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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
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
fun FeedScreen(state: FeedState, user: Member?, select: (String) -> Unit, refresh: () -> Unit, more: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit, search: () -> Unit, login: () -> Unit, categories: () -> Unit, modifier: Modifier = Modifier, category: Category? = null, back: () -> Unit = {}, listStyle: TopicListStyle = TopicListStyle.FEED, pages: Map<String, FeedState> = emptyMap(), categoryContent: (@Composable () -> Unit)? = null, createTopic: (Int) -> Unit = {}, glass: GlassMode = GlassMode.LIQUID) {
    if (category != null) {
        CategoryFeedScreen(category, state, user, select, refresh, more, openTopic, openUser, search, login, back, modifier, listStyle, pages, createTopic, glass)
        return
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val resetHeader = { scrollBehavior.state.heightOffset = 0f; scrollBehavior.state.contentOffset = 0f }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        Row(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
            val offset = scrollBehavior.state.heightOffset.roundToInt()
            layout(placeable.width, (placeable.height + offset).coerceAtLeast(0)) { placeable.placeRelative(0, offset) }
        }.background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(40.dp).clickable(onClick = login), contentAlignment = Alignment.Center) {
                if (user == null) Image(painterResource(R.drawable.ic_linux_do), "Linux.do 社区 Logo", Modifier.size(28.dp))
                else Avatar(user, 28.dp, presentation = AvatarPresentation.ACCOUNT)
            }
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = search), contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxWidth().heightIn(min = 30.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("真诚 · 友善 · 团结 · 专业", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.Search, "搜索", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val tabs = FeedFilter.available(user != null).map {
            it.value to if (it == FeedFilter.NEW && state.newCount != null) "新 (${state.newCount})" else it.label
        } + listOf("categories" to "类别")
        key(user?.id, user?.unifiedNew) {
            val pager = rememberPagerState(initialPage = tabs.indexOfFirst { it.first == state.filter }.coerceAtLeast(0), pageCount = { tabs.size })
            val scope = rememberCoroutineScope()
            val currentSelect by rememberUpdatedState(select)
            val currentCategories by rememberUpdatedState(categories)
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
                    val value = tabs.getOrNull(index)?.first ?: return@collect
                    if (value == "categories") currentCategories() else currentSelect(value)
                    scrollBehavior.state.heightOffset = 0f
                    scrollBehavior.state.contentOffset = 0f
                }
            }
            LaunchedEffect(state.filter) {
                val index = tabs.indexOfFirst { it.first == state.filter }
                if (index >= 0 && pager.currentPage != index && !pager.isScrollInProgress) pager.scrollToPage(index)
            }
            FeedTabBar(tabs, tabs.getOrNull(pager.currentPage)?.first ?: state.filter) { value ->
                val index = tabs.indexOfFirst { it.first == value }
                if (index >= 0) scope.launch { pager.animateScrollToPage(index) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("home-pager"), key = { tabs[it].first }) { index ->
                val value = tabs[index].first
                if (value == "categories") {
                    categoryContent?.invoke() ?: EmptyState("社区分类", "按分类浏览话题", actionLabel = "查看分类", action = categories)
                } else {
                    val page = if (value == state.filter) state else pages[value]?.takeIf { it.accountId == user?.id }
                        ?: FeedState(filter = value, accountId = user?.id, categories = state.categories)
                    FeedPage(page, pager.settledPage == index && value == state.filter, resetHeader,
                        refresh, more, openTopic, openUser, login, listStyle,
                        headerScrolled = scrollBehavior.state.heightOffset < 0f, expandHeader = {
                            androidx.compose.animation.core.animate(scrollBehavior.state.heightOffset, 0f) { value, _ -> scrollBehavior.state.heightOffset = value }
                            scrollBehavior.state.contentOffset = 0f
                        })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedPage(state: FeedState, active: Boolean, scrollToTop: () -> Unit,
    refresh: () -> Unit, more: () -> Unit, openTopic: (Int) -> Unit, openUser: (String) -> Unit,
    login: () -> Unit, listStyle: TopicListStyle, nested: NestedScrollConnection? = null,
    headerScrolled: Boolean = false, expandHeader: suspend () -> Unit = { scrollToTop() }) {
    val ignored = com.kudo.android.ui.topic.LocalIgnoredUsers.current
    val visibleTopics = state.topics.filterNot { it.author?.username?.lowercase(java.util.Locale.ROOT) in ignored }
        val listState = rememberLazyListState()
        val scrollContext = "${state.accountId}:${state.filter}"
        var previousContext by rememberSaveable { mutableStateOf(scrollContext) }
        var consumedScrollRequest by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(scrollContext, state.scrollToTopRequest) {
            if (previousContext != scrollContext || consumedScrollRequest != state.scrollToTopRequest) {
                listState.scrollToItem(0)
                scrollToTop()
                previousContext = scrollContext
                consumedScrollRequest = state.scrollToTopRequest
            }
        }
        val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; info.totalItemsCount > 3 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 } }
        LaunchedEffect(active, nearEnd, state.next) { if (active && nearEnd && state.next != null && state.moreError == null) more() }
        PullToRefreshBox(isRefreshing = state.loading && state.topics.isNotEmpty(), onRefresh = { if (active) refresh() }, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val traditional = listStyle == TopicListStyle.TITLE
            LazyColumn(state = listState, contentPadding = if (traditional) PaddingValues(bottom = 112.dp) else PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(if (traditional) 0.dp else 8.dp), modifier = Modifier.fillMaxSize().then(if (nested != null) Modifier.nestedScroll(nested) else Modifier).testTag("feed-list-${state.filter}")
                .quickScrollTarget(listState, enabled = active, extraScrolled = headerScrolled, expandHeader = expandHeader)) {
                if (state.loading && state.topics.isEmpty()) item { LoadingRow() }
                if (state.error != null) item { ErrorState(state.error, refresh, login) }
                if (!state.loading && state.error == null && visibleTopics.isEmpty()) item { EmptyState("暂时没有可显示话题", "换个分类，或者稍后再来看看。") }
                items(visibleTopics, key = { "topic-${it.id}" }, contentType = { listStyle }) { topic ->
                    val category = state.categories.firstOrNull { it.id == topic.categoryId }
                    if (traditional) TopicTitleRow(topic, category, { openTopic(topic.id) }, openUser)
                    else TopicCard(topic, category, { openTopic(topic.id) }, openUser)
                }
                if (state.loadingMore) item { LoadingRow() }
                else if (state.moreError != null) item { ErrorState(state.moreError, more, login) }
                else if (state.next != null) item { TextButton(onClick = more, modifier = Modifier.fillMaxWidth()) { Text("加载更多话题") } }
                else if (state.topics.isNotEmpty()) item { Text("已经看到这里的全部话题", Modifier.fillMaxWidth().padding(14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

}

@Composable
internal fun FeedTabBar(tabs: List<Pair<String, String>>, selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).horizontalScroll(rememberScrollState()).selectableGroup().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { (value, label) ->
            key(value) {
                val active = selected == value
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(active) { if (active) bringIntoView.bringIntoView() }
                Box(Modifier.bringIntoViewRequester(bringIntoView).selectable(active, role = Role.Tab, onClick = { select(value) }).heightIn(min = 34.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, Modifier.padding(vertical = 5.dp).drawBehind {
                        if (active) drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(LogoYellow.copy(alpha = 0.25f), LogoYellow.copy(alpha = 0.95f), LogoYellow.copy(alpha = 0.4f))),
                            topLeft = Offset(0f, size.height * 0.58f), size = Size(size.width, size.height * 0.34f),
                            cornerRadius = CornerRadius(3.dp.toPx()))
                    }, fontSize = if (active) 17.sp else 15.sp, lineHeight = if (active) 24.sp else 21.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
