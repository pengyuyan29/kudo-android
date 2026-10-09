package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TopicPresentationUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "author", "测试楼主", "", avatarDetailsLoaded = true)
    private val category = Category(4, "开发调优", "dev", "35a9b7", "", 0, "code")
    private val tags = listOf(TopicTag("纯水", "1461-tag"), TopicTag("人工智能", "444-tag"), TopicTag("普通标签"))
    private val topic = Topic(3, "传统标题列表中的帖子标题", "只在信息流显示的摘要", author, 4,
        tags.map { it.name }, 109, 210, 9, "", "2026-10-09T00:00:00Z", tagDetails = tags)
    private val first = Post(10, 1, author, "<p>主帖内容</p>", "2026-10-08T00:00:00Z", 0, false, null, null, true,
        updatedAt = "2026-10-09T00:00:00Z", version = 3)
    private val detail = TopicDetail(3, topic.title, 4, listOf(first), listOf(10), false, tags = tags)
    private val metadata = TopicPresentationMetadata(categories = listOf(category))

    @Test fun feedShowsTimeCategoryAndCapsuleIconsWithoutEditMarker() {
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalTopicPresentation provides metadata) {
                    TopicCard(topic, category, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("分类图标：开发调优").assertIsDisplayed()
        compose.onNodeWithContentDescription("标签图标：纯水").assertIsDisplayed()
        compose.onNodeWithContentDescription("标签图标：人工智能").assertIsDisplayed()
        compose.onNodeWithTag("topic-tag-普通标签", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("已编辑").assertDoesNotExist()
        capture("presentation-feed-light.png")
    }

    @Test fun detailHeaderShowsRealEditStateAndReplyMetadataUsesSameCategory() {
        var post by mutableStateOf(first)
        val reply = first.copy(id = 11, number = 2, author = author.copy(id = 2, username = "reply", name = "回复者"), version = 1)
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                CompositionLocalProvider(LocalTopicPresentation provides metadata) {
                    TopicScreen(TopicState(detail.copy(posts = listOf(post, reply), stream = listOf(10, 11)), false, viewerId = 1),
                        GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("topic-author").assert(hasText("已编辑"))
        compose.onAllNodesWithContentDescription("分类图标：开发调优").assertCountEquals(2)
        compose.onNodeWithContentDescription("标签图标：人工智能").assertIsDisplayed()
        capture("presentation-detail-light.png")
        compose.runOnIdle { post = first.copy(version = 1) }
        compose.onNodeWithText("已编辑").assertDoesNotExist()
    }

    @Test fun floorLinkLocatesRequestedReplyInsteadOfStartOfReturnedChunk() {
        val replies = (5..12).map { n -> first.copy(id = 100 + n, number = n, cooked = "<p>第${n}楼内容</p>", version = 1,
            author = author.copy(id = n, name = "楼层作者$n")) }
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                TopicScreen(TopicState(detail.copy(posts = replies, stream = replies.map { it.id }, pinnedOriginal = first), false,
                    viewerId = 1, targetNumber = 8), GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("第8楼内容").assertIsDisplayed()
        compose.onNodeWithText("第5楼内容").assertDoesNotExist()
        capture("presentation-floor-eight.png")
    }

    @Test fun replyWidthStaysContentSizedWhenParentGrowsWithEqualInsets() {
        var width by mutableStateOf(320.dp)
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                Box(Modifier.width(width).fillMaxHeight()) {
                    TopicScreen(TopicState(detail, false, viewerId = 1), GlassMode.TRANSLUCENT,
                        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        val button = compose.onNodeWithTag("topic-reply").fetchSemanticsNode().boundsInRoot
        val icon = compose.onNodeWithTag("topic-reply-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText("写回复", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("按钮左右内边距相等", icon.left - button.left, button.right - text.right, 1f)
        compose.runOnIdle { width = 360.dp }
        assertEquals("父容器变宽时按钮不能按比例撑大", button.width,
            compose.onNodeWithTag("topic-reply").fetchSemanticsNode().boundsInRoot.width, 1f)
        capture("presentation-reply-width.png")
    }

    @Test fun traditionalRowWrapsChipsOnNarrowDarkLargeText() {
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                CompositionLocalProvider(LocalTopicPresentation provides metadata) {
                    Box(Modifier.width(280.dp)) { TopicTitleRow(topic, category, {}, {}) }
                }
            }
        }
        tags.forEach { compose.onNodeWithTag("topic-tag-${it.name}", useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithTag("topic-count-3", useUnmergedTree = true).assertIsDisplayed()
        val row = compose.onNodeWithTag("topic-title-3").fetchSemanticsNode().boundsInRoot
        tags.forEach { assertTrue(compose.onNodeWithTag("topic-tag-${it.name}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right <= row.right) }
        capture("presentation-title-dark-large.png")
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
