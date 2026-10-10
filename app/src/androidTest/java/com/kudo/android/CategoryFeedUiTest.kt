package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CategoryFeedUiTest {
    @get:Rule val compose = createComposeRule()
    private val category = Category(4, "开发调优", "develop", "32c3c3", "此版块包含开发、测试、调试、部署、优化、安全等方面的内容。", 1, "code")
    private fun page(filter: String = "latest", count: Int = 40) = FeedState(filter = filter, loading = false,
        categories = listOf(category), topics = (1..count).map {
            Topic(it, "$filter 话题 $it", "验证分类滚动和独立分页的内容", null, 4, emptyList(), 0, 0, 0, "", "")
        })

    @Test fun categoryHeaderShowsCatalogIdentityAndDescription() {
        compose.setContent { KudoTheme(DisplaySettings()) {
            FeedScreen(page(), null, {}, {}, {}, {}, {}, {}, {}, {}, category = category)
        } }
        compose.onNodeWithText(category.description).assertIsDisplayed()
        compose.onNodeWithTag("category-expanded-icon").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索").assertIsDisplayed()
        capture("category-expanded.png")
    }

    @Test fun listCollapsesFirstThenReturnsToTopBeforeExpandingAndKeepsHeaderColor() {
        compose.setContent { KudoTheme(DisplaySettings()) {
            FeedScreen(page(), null, {}, {}, {}, {}, {}, {}, {}, {}, category = category)
        } }
        val initial = compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top
        val toolbar = compose.onNodeWithTag("category-toolbar").captureToImage().toPixelMap()
        val background = toolbar[toolbar.width / 2, toolbar.height / 2]
        val list = compose.onNodeWithTag("feed-list-latest")
        list.performTouchInput { swipe(center, center.copy(y = center.y - 90f), durationMillis = 1000) }
        assertTrue(compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top < initial - 50f)
        compose.onNodeWithText("latest 话题 1").assertIsDisplayed()
        repeat(3) { list.performTouchInput { swipeUp(durationMillis = 600) } }
        val pinned = compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("category-toolbar-icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("category-toolbar-name").assertTextEquals(category.name)
        compose.onNodeWithText(category.description).assertIsNotDisplayed()
        val collapsed = compose.onNodeWithTag("category-toolbar").captureToImage().toPixelMap()
        assertEquals(background, collapsed[collapsed.width - 3, collapsed.height / 2])
        capture("category-collapsed.png")
        list.performTouchInput { swipe(center, center.copy(y = center.y + 90f), durationMillis = 1000) }
        assertEquals(pinned, compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top, 2f)
        repeat(8) { list.performTouchInput { swipeDown(durationMillis = 600) } }
        assertEquals(initial, compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun listFlingCollapsesHeaderAndShortPageExpandsBeforeRefreshing() {
        var refreshes = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            FeedScreen(page(count = 1), null, {}, { refreshes++ }, {}, {}, {}, {}, {}, {}, category = category)
        } }
        val list = compose.onNodeWithTag("feed-list-latest")
        val initial = compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top
        list.performTouchInput { swipe(center.copy(y = height * .8f), center.copy(y = height * .65f), durationMillis = 80) }
        compose.onNodeWithTag("category-toolbar-icon", useUnmergedTree = true).assertIsDisplayed()
        list.performTouchInput { swipeDown(durationMillis = 800) }
        assertEquals(initial, compose.onNodeWithText("最新").fetchSemanticsNode().boundsInRoot.top, 2f)
        list.performTouchInput { swipeDown(durationMillis = 800) }
        compose.runOnIdle { assertTrue("顶部展开后可以继续下拉刷新", refreshes > 0) }
    }

    @Test fun metadataCanArriveLaterAndDarkNarrowLargeTextKeepsActions() {
        var current by mutableStateOf(category.copy(name = "社区分类", color = "bad-color", description = "", icon = "future-icon"))
        var backs = 0
        var searches = 0
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Box(Modifier.fillMaxSize()) { FeedScreen(page(), null, {}, {}, {}, {}, {}, { searches++ }, {}, {},
                Modifier.width(280.dp), category = current, back = { backs++ }, listStyle = TopicListStyle.TITLE) }
        } }
        compose.onNodeWithText("社区分类").assertIsDisplayed()
        compose.runOnIdle { current = category }
        compose.onNodeWithText(category.description).assertIsDisplayed()
        capture("category-dark-narrow-expanded.png")
        compose.onNodeWithTag("feed-list-latest").performTouchInput { swipeUp(durationMillis = 800) }
        compose.onNodeWithTag("category-toolbar-name").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(1, searches) }
        capture("category-dark-narrow-collapsed.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
