package com.kudo.android

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TopicFloorNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(1, "fixture", "离线作者", "", avatarDetailsLoaded = true)
    private fun post(number: Int, parent: Int? = null) = Post(number, number, author, "<p>正文第 $number 楼</p>", "", 0, false, null, parent, true)
    private val posts = listOf(post(1), post(2), post(8), post(20), post(80, 8))
    private val detail = TopicDetail(5, "真实楼层索引", 1, posts, posts.map(Post::id), false, highestPostNumber = 100)

    @Test fun sliderOnlyPreviewsAndConfirmedNestedFloorTargetsTheMainTimeline() {
        var state by mutableStateOf(TopicState(detail, false))
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, jumpTo = {
                requests++; state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1)
            })
        } }
        compose.onNodeWithText("5 个楼层").assertDoesNotExist()
        compose.onNodeWithTag("topic-floor-index").assertIsDisplayed().performClick()
        compose.onNodeWithTag("floor-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        compose.onNodeWithTag("floor-input").assertTextEquals("60")
        capture("floor-jump-sheet-light.png")
        compose.onNodeWithTag("floor-slider").performTouchInput {
            swipe(centerLeft + androidx.compose.ui.geometry.Offset(1f, 0f), centerRight - androidx.compose.ui.geometry.Offset(1f, 0f), 600)
        }
        compose.onNodeWithTag("floor-input").assertTextEquals("100")
        compose.onNodeWithText("首楼").performClick()
        compose.onNodeWithTag("floor-input").assertTextEquals("1")
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("floor-input").performTextReplacement("101")
        compose.onNodeWithTag("floor-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("floor-input").performTextReplacement("80")
        compose.onNodeWithTag("floor-confirm").assertIsEnabled().performClick()
        compose.onNodeWithTag("floor-input").assertDoesNotExist()
        compose.onNodeWithTag("thread-posts").assertDoesNotExist()
        compose.onNodeWithTag("post-80").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun scrollingUsesActualNumbersAndRepeatedJumpRepositionsTheList() {
        val scrollPosts = posts + listOf(post(30), post(40), post(100))
        var state by mutableStateOf(TopicState(detail.copy(posts = scrollPosts, stream = scrollPosts.map(Post::id)), false))
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                jumpTo = { state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1) })
        } }
        repeat(2) {
            compose.onNodeWithTag("topic-posts").performScrollToIndex(4)
            compose.onNodeWithText("20 / 100").assertIsDisplayed()
            if (it == 0) capture("floor-progress-light.png")
            compose.onNodeWithTag("topic-floor-index").performClick()
            compose.onNodeWithTag("floor-input").performTextReplacement("8")
            compose.onNodeWithTag("floor-confirm").performClick()
            compose.onNodeWithText("8 / 100").assertIsDisplayed()
            compose.onNodeWithTag("post-8").assertIsDisplayed()
        }
    }

    @Test fun aFarFloorJumpAlignsTheRequestedPostInsteadOfStoppingAtTheLoadedWindow() {
        val loaded = (1..20).map(::post) + post(80, 20) + (81..200).map(::post)
        var state by mutableStateOf(TopicState(detail.copy(posts = loaded, stream = loaded.map(Post::id)), false))
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                jumpTo = { state = state.copy(targetNumber = it, targetRevision = state.targetRevision + 1) })
        } }
        val viewport = compose.onNodeWithTag("topic-posts").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("topic-floor-index").performClick()
        compose.onNodeWithTag("floor-input").performTextReplacement("80")
        compose.onNodeWithTag("floor-confirm").performClick()
        compose.waitForIdle()
        val targetTop = compose.onNodeWithTag("post-80").fetchSemanticsNode().boundsInRoot.top
        assertTrue("far jump must leave the requested post visible: target=" + targetTop + " viewportBottom=" + viewport.bottom,
            targetTop < viewport.bottom)
        compose.onNodeWithText("80 / 100").assertIsDisplayed()
    }

    @Test fun narrowDarkLargeTextKeepsAllControlsAndFailureKeepsTheForm() {
        var state by mutableStateOf(TopicState(detail.copy(highestPostNumber = 120000), false))
        var mode by mutableStateOf(GlassMode.LIQUID)
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Box(Modifier.width(280.dp).fillMaxHeight()) {
                TopicScreen(state, mode, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                    jumpTo = { state = state.copy(jumping = true) })
            }
        } }
        GlassMode.entries.forEach { glass ->
            compose.runOnIdle { mode = glass }
            val reply = compose.onNodeWithTag("topic-reply").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val index = compose.onNodeWithTag("topic-floor-index").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("topic-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(reply.right < index.left); assertTrue(index.right < actions.left)
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("1 / 120000").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertFalse(results.single().hasVisualOverflow)
            capture("floor-${glass.name.lowercase()}-narrow-dark-large.png")
        }
        compose.onNodeWithTag("topic-floor-index").performClick()
        compose.onNodeWithText("末楼").performClick()
        compose.onNodeWithTag("floor-input").assertTextEquals("120000")
        compose.onNodeWithTag("floor-confirm").performClick().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(jumping = false, jumpError = "此楼层暂不可访问") }
        compose.onNodeWithText("此楼层暂不可访问").assertIsDisplayed()
        compose.onNodeWithTag("floor-input").assertTextEquals("120000")
        compose.onNodeWithTag("sheet-drag-handle").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 650f), 600)
        }
        compose.onNodeWithTag("floor-input").assertDoesNotExist()
    }

    @Test fun theFloorPanelAndPendingJumpPauseReadingWithoutMarkingSkippedFloors() {
        var state by mutableStateOf(TopicState(detail, false, viewerId = 9))
        var active by mutableStateOf(false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(state, GlassMode.TRANSLUCENT, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                readingEnabled = active, onPostsRead = { state = state.copy(readNumbers = state.readNumbers + it) },
                jumpTo = { state = state.copy(jumping = true) })
        } }
        compose.onNodeWithTag("topic-floor-index").performClick()
        compose.runOnIdle { active = true }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { assertTrue(state.readNumbers.isEmpty()) }
        compose.onNodeWithTag("floor-input").performTextReplacement("20")
        compose.onNodeWithTag("floor-confirm").performClick()
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle {
            assertTrue(state.readNumbers.isEmpty())
            state = state.copy(jumping = false, targetNumber = 20, targetRevision = 1)
        }
        compose.waitUntil(4000) { 20 in state.readNumbers }
        // At the end of a short list, earlier bodies can remain genuinely visible beside the target.
        compose.runOnIdle {
            // Child 80 is now also an original timeline item and may be genuinely visible.
            assertTrue(state.readNumbers.all { it in setOf(2, 8, 20, 80) })
            assertFalse(1 in state.readNumbers)
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
