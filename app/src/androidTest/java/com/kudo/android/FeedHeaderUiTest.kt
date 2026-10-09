package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FeedHeaderUiTest {
    @get:Rule val compose = createComposeRule()

    private fun content() {
        val topics = (1..30).map { Topic(it, "吸顶测试话题 $it", "用于验证顶部搜索折叠及列表滚动的内容。", null, 1, emptyList(), 0, 0, 0, "", "") }
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                FeedScreen(FeedState(topics = topics, loading = false), null, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun scrollingCollapsesSearchPinsTabsAndReverseExpandsSearch() {
        content()
        val initialTop = compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top
        val list = compose.onNodeWithTag("feed-list-latest")
        list.performTouchInput { swipeUp(durationMillis = 800) }
        compose.onNodeWithText("最新").assertIsDisplayed()
        val pinnedTop = compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top
        assertTrue("上滑后 Tab 应移到搜索行原位置并吸顶", pinnedTop < initialTop - 40f)
        compose.onNodeWithContentDescription("Linux.do 社区 Logo").assertIsNotDisplayed()
        list.performTouchInput { swipeUp(durationMillis = 800) }
        assertEquals(pinnedTop, compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top, 1f)
        list.performTouchInput { swipeDown(durationMillis = 800) }
        compose.onNodeWithContentDescription("Linux.do 社区 Logo").assertIsDisplayed()
        assertEquals(initialTop, compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun feedStartsWithTopicsWithoutBannerOrShortcuts() {
        content()
        compose.onNodeWithText("认真交流，自在分享").assertDoesNotExist()
        listOf("新鲜事", "热议中", "值得看", "逛分类").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("吸顶测试话题 1").assertIsDisplayed()
    }

    @Test fun accountTabsScrollSelectAndResetToGuestWithoutPersonalEntries() {
        var user by mutableStateOf<Member?>(null)
        var state by mutableStateOf(FeedState(loading = false))
        var categories = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                FeedScreen(state, user, { state = state.copy(filter = it) }, {}, {}, {}, {}, {}, {}, { categories++ })
            }
        }
        listOf("最新", "热门", "排行榜", "类别").forEach { compose.onNodeWithText(it).assertExists() }
        listOf("未读", "新", "我的帖子", "已读", "书签").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("最新").assertIsSelected()
        capture("home-tabs-guest.png")
        compose.runOnIdle {
            user = Member(1, "fixture", "测试用户", "")
            state = FeedState(filter = "unread", loading = false, accountId = 1, newCount = 5096)
        }
        compose.onNodeWithText("未读").assertIsSelected()
        compose.onNodeWithText("新 (5096)").assertExists()
        capture("home-tabs-member.png")
        for (label in listOf("最新", "新 (5096)", "热门", "排行榜", "我的帖子", "已读", "书签")) {
            compose.onNodeWithText(label).performScrollTo().performClick().assertIsSelected()
        }
        compose.onNodeWithText("类别").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, categories) }
        capture("home-tabs-member-end.png")
        compose.runOnIdle { user = null; state = FeedState(loading = false) }
        compose.onNodeWithText("最新").assertIsDisplayed().assertIsSelected()
        listOf("未读", "新 (5096)", "我的帖子", "已读", "书签").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun darkLargeTextAndNarrowWindowKeepLabelsWholeAndAllowRefreshAfterCollapse() {
        var state by mutableStateOf(FeedState(filter = "unread", loading = false, accountId = 1))
        var refreshes = 0
        val user = Member(1, "fixture", "测试用户", "")
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                Box(Modifier.fillMaxSize()) {
                FeedScreen(state, user, { state = state.copy(filter = it) }, { refreshes++ }, {}, {}, {}, {}, {}, {}, Modifier.width(280.dp))
                }
            }
        }
        compose.onNodeWithText("新").assertExists()
        capture("home-tabs-dark-large-initial.png")
        for (label in listOf("未读", "最新", "新", "热门", "排行榜", "我的帖子", "已读", "书签", "类别")) {
            val node = compose.onNodeWithText(label).performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val glyphs = label.indices.map(layout::getBoundingBox)
            println("Tab glyph bounds: $label size=${layout.size}, overflow=${layout.hasVisualOverflow}, glyphs=$glyphs")
            assertTrue("$label 字形不应超出布局", glyphs.all { it.left >= -1f && it.top >= -1f && it.right <= layout.size.width + 1f && it.bottom <= layout.size.height + 1f })
        }
        capture("home-tabs-dark-large-end.png")
        compose.onNodeWithText("未读").performScrollTo().performClick()
        compose.onNodeWithTag("feed-list-unread").performTouchInput { swipeUp(durationMillis = 600) }
        compose.onNodeWithText("未读").assertIsDisplayed()
        compose.onNodeWithTag("feed-list-unread").performTouchInput { swipeDown(durationMillis = 800) }
        compose.onNodeWithTag("feed-list-unread").performTouchInput { swipeDown(durationMillis = 800) }
        compose.runOnIdle { assertTrue("顶部展开后仍能下拉刷新", refreshes > 0) }
        capture("home-tabs-dark-large.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
