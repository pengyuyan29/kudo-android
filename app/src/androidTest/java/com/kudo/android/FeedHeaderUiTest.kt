package com.kudo.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kudo.android.data.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        val list = compose.onAllNodes(hasScrollAction())[1]
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
}
