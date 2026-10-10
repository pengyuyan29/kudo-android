package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.components.Avatar
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

    @Test fun boostAvatarsHideDecorationsWithoutChangingOtherAvatarScenes() {
        val flaired = author.copy(flair = AvatarFlair("测试资质", "battery-quarter"))
        val status = author.copy(id = 2, username = "status", status = UserStatus("smile", "测试状态"))
        var opened = ""
        compose.setContent { KudoTheme(DisplaySettings()) { Column {
            PostBoosts(first.copy(boosts = listOf(PostBoost(100, flaired, "<p>加油😀</p>"),
                PostBoost(102, status, "<p>支持一下</p>"))), true, { opened = it }, {})
            Avatar(flaired, 40.dp)
            Avatar(status, 40.dp)
        } } }
        val chips = hasAnyAncestor(hasTestTag("post-boosts-1"))
        compose.onAllNodes(hasContentDescription("的资质", substring = true) and chips, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("的状态", substring = true) and chips, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("的资质", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("的状态", substring = true, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("加油😀").assertIsDisplayed()
        compose.onNodeWithTag("boost-100").performClick()
        compose.runOnIdle { assertEquals(author.username, opened) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "boost-plain-avatars.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

    @Test fun replyFloorBoostsAndFormWorkInDarkLargeText() {
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            TopicScreen(TopicState(detail, false, viewerId = 9, targetNumber = 3, boostEmojis = emptySet()),
                GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("boost-101").performScrollTo().assertIsDisplayed()
        compose.onNode(hasContentDescription("Boost 此帖子") and hasAnyAncestor(hasTestTag("post-3")))
            .performScrollTo().performClick()
        compose.onNodeWithTag("boost-input").performTextInput("可以继续加油😀")
        compose.onNodeWithText("发送").assertIsEnabled()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "boost-dark-large.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onAllNodesWithTag("sheet-drag-handle").onLast().performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 650f), 600)
        }
        compose.onNodeWithTag("boost-input").assertDoesNotExist()
        compose.onNodeWithTag("post-3").assertIsDisplayed()
    }
}
