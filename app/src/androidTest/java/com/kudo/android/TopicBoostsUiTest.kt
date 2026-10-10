package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap

class TopicBoostsUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "author", "作者", "", avatarDetailsLoaded = true)
    private val first = Post(1, 1, author, "<p>主帖正文</p>", "", 0, false, null, null, true,
        boosts = listOf(PostBoost(100, author, "<p>支持一下😀</p>")), canBoost = true)
    private val root = first.copy(id = 2, number = 2, cooked = "<p>评论正文</p>", boosts = emptyList())
    private val child = first.copy(id = 3, number = 3, replyTo = 2, cooked = "<p>楼中楼正文</p>", boosts = listOf(PostBoost(101, author, "<p>回复也可以 Boost</p>")))
    private val detail = TopicDetail(5, "帖子与 Boost", 1, listOf(first, root, child), listOf(1, 2, 3), false)

    @Test fun boostInputEnforcesBothLimitsAndOnlyClearsAfterConfirmation() {
        var state by mutableStateOf(TopicState(detail, false, viewerId = 9, boostEmojis = emptySet()))
        var requests = 0
        var accepted: (() -> Unit)? = null
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                boost = { post, _, done -> assertEquals(1, post.id); requests++; accepted = done })
        } }
        compose.onNodeWithTag("boost-100").assertExists()
        compose.onNodeWithContentDescription("Boost 主帖").performClick()
        compose.onNodeWithTag("boost-input").assertIsFocused().performTextInput("😀".repeat(6))
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithTag("boost-input").performTextReplacement("字".repeat(17))
        compose.onNodeWithText("发送").assertIsNotEnabled()
        compose.onNodeWithTag("boost-input").performTextReplacement("短回复加油")
        compose.onNodeWithText("发送").assertIsEnabled().performClick()
        compose.onNodeWithTag("boost-input").assertTextEquals("短回复加油")
        compose.runOnIdle { assertEquals(1, requests); state = state.copy(busy = setOf(1)) }
        compose.onNodeWithText("发送中…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("关闭 Boost").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = emptySet()); accepted!!.invoke() }
        compose.onNodeWithTag("boost-input").assertDoesNotExist()
        compose.onNodeWithContentDescription("Boost 主帖").performClick()
        compose.onNodeWithTag("boost-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun nestedBoostsAppearAndTheFormWorksInDarkLargeText() {
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            TopicScreen(TopicState(detail, false, viewerId = 9, targetNumber = 3, boostEmojis = emptySet()),
                GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("thread-posts").performScrollToNode(hasTestTag("boost-101"))
        compose.onNodeWithTag("boost-101").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Boost 此帖子").onLast().performClick()
        compose.onNodeWithTag("boost-input").performTextInput("可以继续加油😀")
        compose.onNodeWithText("发送").assertIsEnabled()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "boost-dark-large.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onAllNodesWithTag("sheet-drag-handle").onLast().performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 650f), 600)
        }
        compose.onNodeWithTag("boost-input").assertDoesNotExist()
        compose.onNodeWithTag("thread-posts").assertExists()
    }
}
