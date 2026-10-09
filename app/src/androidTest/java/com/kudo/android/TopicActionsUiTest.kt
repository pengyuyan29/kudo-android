package com.kudo.android

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TopicActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(2, "author", "测试作者", "")
    private val viewer = Member(1, "viewer", "当前用户", "", canIgnoreUsers = true)
    private val post = Post(10, 1, author, "<p>复制的主帖 <b>纯文本</b></p>", "2026-10-09T00:00:00Z", 0, false, null, null, false,
        version = 3, canViewEditHistory = true)
    private val topic = Topic(5, "置顶测试话题", "摘要内容", author, 1, emptyList(), 8, 10, 1, "", "", pinned = true)
    private val repository = CommunityRepository(DiscourseClient(object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }))

    @Test fun pinnedFeedAndTitleRowsOpenActionsFromLongPressAndEllipsis() {
        var count = 0
        var opened = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                CompositionLocalProvider(LocalTopicActions provides TopicActionsController({ count++; opened = it }, { _, _, _ -> })) {
                    Column { TopicCard(topic, null, {}, {}); TopicTitleRow(topic, null, {}, {}) }
                }
            }
        }
        compose.onNodeWithTag("topic-card-5").performTouchInput { longClick() }
        compose.onNodeWithTag("topic-title-5").performTouchInput { longClick() }
        compose.onAllNodesWithContentDescription("主题操作")[0].performClick()
        compose.onAllNodesWithContentDescription("主题操作")[1].performClick()
        compose.runOnIdle { assertEquals(4, count); assertEquals(5, opened) }
    }

    @Test fun detailMenuCopiesMainPostAndCancelAndBackRestoreBackground() {
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
                TopicActionsHost(repository, viewer, GlassMode.TRANSLUCENT, {}, { _, _ -> }, {}) {
                    val actions = LocalTopicActions.current
                    Button({ actions.openDetail(5, post, {}) }) { Text("打开菜单") }
                }
            }
        }
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithText("打开菜单").assertDoesNotExist()
        compose.onNodeWithText("屏蔽").assertDoesNotExist()
        compose.onNodeWithText("编辑历史").assertIsDisplayed()
        capture("topic-actions-detail-light.png")
        compose.onNodeWithText("复制").performClick()
        compose.onNodeWithText("打开菜单").assertIsDisplayed()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals("复制的主帖 纯文本", (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("打开菜单").assertIsDisplayed()
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithTag("liquid-action-sheet").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("打开菜单").assertIsDisplayed()
    }

    @Test fun detailBookmarkDelegatesToExistingStateAndReopeningShowsCancellation() {
        var current by mutableStateOf(post)
        var writes = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                TopicActionsHost(repository, viewer, GlassMode.TRANSLUCENT, {}, { _, _ -> }, {}) {
                    val actions = LocalTopicActions.current
                    Button({ actions.openDetail(5, current) { writes++; current = it.copy(bookmarkId = 99) } }) { Text("打开菜单") }
                }
            }
        }
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithText("收藏").performClick()
        compose.runOnIdle { assertEquals(1, writes); assertEquals(99, current.bookmarkId) }
        compose.onNodeWithText("打开菜单").performClick()
        compose.onNodeWithText("取消收藏").assertIsDisplayed()
    }

    @Test fun listMenuHasHideWithoutCopyAndNoHistoryWithoutBothPermissionAndVersion() {
        var current by mutableStateOf(post.copy(version = 1))
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                Box(Modifier.width(280.dp)) { TopicActionMenu(current, false, true, {}, {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("屏蔽").assertIsDisplayed()
        compose.onNodeWithText("复制").assertDoesNotExist()
        compose.onNodeWithText("编辑历史").assertDoesNotExist()
        compose.runOnIdle { current = post.copy(canViewEditHistory = false) }
        compose.onNodeWithText("编辑历史").assertDoesNotExist()
        compose.runOnIdle { current = post }
        compose.onNodeWithText("编辑历史").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("举报").performScrollTo().assertIsDisplayed()
        capture("topic-actions-list-dark-large.png")
    }

    @Test fun reportingRequiresSiteMinimumAndIllegalConfirmationAndShowsCustomReasons() {
        val illegal = FlagReason(10, "illegal", "非法", "违法内容", true)
        val other = FlagReason(1002, "custom_aigc", "AIGC未截图", "请截图", false)
        val config = FlagConfiguration(listOf(other, illegal), 3)
        var state by mutableStateOf(TopicActionState(post = post, flags = config))
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                Column(Modifier.padding(16.dp)) {
                    TopicReportContent(state, { state = state.copy(selectedFlag = it) }, { state = state.copy(message = it) }, { state = state.copy(confirmed = it) })
                    Button({}, enabled = config.valid(state.selectedFlag, state.message, state.confirmed)) { Text("提交举报") }
                }
            }
        }
        compose.onNodeWithText("AIGC未截图").assertIsDisplayed()
        compose.onNodeWithText("提交举报").assertIsNotEnabled()
        compose.onNodeWithText("非法", substring = false).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("说明文")
        compose.onNodeWithText("提交举报").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("提交举报").assertIsEnabled()
        capture("topic-report-validation.png")
    }

    @Test fun historyShowsDifferencesAndUsesNonconsecutiveServerNavigation() {
        var selected: Int? = null
        val revision = PostRevision(10, 4, 1, 7, "编辑作者", "2026-10-09T00:00:00Z", "补充说明", listOf(
            RevisionChange("正文", listOf(RevisionPart("旧内容", RevisionKind.REMOVED), RevisionPart("新内容", RevisionKind.ADDED)))), false, false)
        compose.setContent { KudoTheme(DisplaySettings()) { Column(Modifier.padding(16.dp)) { TopicHistoryContent(TopicActionState(revision = revision)) { selected = it } } } }
        compose.onNodeWithText("编辑原因：补充说明").assertIsDisplayed()
        compose.onNodeWithText("旧内容", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("上一处修订").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
        compose.onNodeWithContentDescription("下一处修订").performClick()
        compose.runOnIdle { assertEquals(7, selected) }
        capture("topic-history-diff.png")
    }

    @Test fun ignoreDialogNamesTheAuthorAndRequiresExplicitDuration() {
        var state by mutableStateOf(TopicActionState(post = post))
        compose.setContent { KudoTheme(DisplaySettings()) { Column(Modifier.padding(16.dp)) {
            TopicIgnoreContent(state, viewer) { state = state.copy(duration = it) }
            Button({}, enabled = state.duration != null) { Text("确认忽略") }
        } } }
        compose.onNodeWithText("忽略 @author").assertIsDisplayed()
        compose.onNodeWithText("确认忽略").assertIsNotEnabled()
        compose.onNodeWithText("永久").performClick()
        compose.onNodeWithText("确认忽略").assertIsEnabled()
        capture("topic-ignore-confirm.png")
    }

    @Test fun ignoredAuthorDisappearsFromBothListStylesAndDetailCanRevealOnePost() {
        var style by mutableStateOf(TopicListStyle.FEED)
        var detail by mutableStateOf(false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            CompositionLocalProvider(LocalIgnoredUsers provides setOf("author")) {
                if (detail) TopicScreen(TopicState(TopicDetail(5, topic.title, 1, listOf(post), listOf(10), false), false), GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                else FeedScreen(FeedState(topics = listOf(topic), loading = false), viewer, {}, {}, {}, {}, {}, {}, {}, {}, listStyle = style)
            }
        } }
        compose.onNodeWithText(topic.title).assertDoesNotExist()
        compose.runOnIdle { style = TopicListStyle.TITLE }
        compose.onNodeWithText(topic.title).assertDoesNotExist()
        compose.runOnIdle { detail = true }
        compose.onNodeWithText("复制的主帖", substring = true).assertDoesNotExist()
        compose.onNodeWithText("查看此楼层").performClick()
        compose.onNodeWithText("复制的主帖", substring = true).assertIsDisplayed()
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
