package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.components.GlassNavigation
import com.kudo.android.ui.feed.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeRefreshUiTest {
    @get:Rule val compose = createComposeRule()
    private fun initialState() = FeedState(loading = false, topics = (1..30).map {
        Topic(it, "更新测试话题 $it", "收到推送时继续阅读，点击刷新后回到顶部。", null, 1, emptyList(), 0, 0, 0, "", "")
    })

    @Test fun incomingPromptPreservesScrollAndClickReturnsToTopUntilRefreshSucceeds() {
        var state by mutableStateOf(initialState())
        var refreshes = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    FeedScreen(state, null, {}, {}, {}, {}, {}, {}, {}, {}, Modifier.layerBackdrop(backdrop))
                    GlassNavigation(0, {
                        if (it == 0 && state.hasUpdates) {
                            refreshes++
                            state = state.copy(scrollToTopRequest = state.scrollToTopRequest + 1)
                        }
                    }, {}, backdrop, GlassMode.LIQUID, Modifier.align(Alignment.BottomCenter).padding(20.dp).fillMaxWidth(), state.hasUpdates)
                }
            }
        }
        val list = compose.onAllNodes(hasScrollAction())[1]
        list.performTouchInput { swipeUp(durationMillis = 800) }
        val position = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(position > 0f)
        compose.runOnIdle { state = state.copy(hasUpdates = true) }
        compose.onAllNodesWithText("刷新").assertCountEquals(1)
        compose.onNodeWithText("首页").assertDoesNotExist()
        assertEquals(position, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.1f)
        capture("home-refresh-scrolled.png")
        compose.onNodeWithText("刷新").performTouchInput { click() }
        compose.onNodeWithText("更新测试话题 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Linux.do 社区 Logo").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText("刷新").assertIsDisplayed()
        capture("home-refresh-top.png")
        compose.runOnIdle { state = state.copy(hasUpdates = false) }
        compose.onNodeWithText("首页").assertIsDisplayed()
        compose.onNodeWithText("刷新").assertDoesNotExist()
    }

    @Test fun refreshEntryRemainsUniqueYellowAndClickableFromAnotherTabInAllMaterials() {
        var selected by mutableIntStateOf(4)
        var mode by mutableStateOf(GlassMode.LIQUID)
        var clicks = 0
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Text("首页有更新", Modifier.layerBackdrop(backdrop))
                    GlassNavigation(selected, { selected = it; clicks++ }, {}, backdrop, mode,
                        Modifier.align(Alignment.BottomCenter).padding(20.dp).width(280.dp), homeHasUpdates = true)
                }
            }
        }
        for ((index, material) in GlassMode.entries.withIndex()) {
            compose.runOnIdle { mode = material; selected = 4 }
            compose.onAllNodesWithText("刷新").assertCountEquals(1)
            compose.onNodeWithText("刷新").performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(index + 1, clicks); assertEquals(0, selected) }
            val pixels = compose.onNodeWithText("刷新").captureToImage().toPixelMap()
            val yellow = (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y ->
                val pixel = pixels[x, y]
                pixel.red > 0.8f && pixel.green in 0.5f..0.8f && pixel.blue < 0.2f
            } }
            assertTrue("刷新文字应为 LogoYellow：$material", yellow > 10)
            capture("home-refresh-${material.name.lowercase()}-dark.png")
        }
    }

    @Test fun returningToSavedFeedDoesNotResetReadingPosition() {
        var visible by mutableStateOf(true)
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val holder = rememberSaveableStateHolder()
                if (visible) holder.SaveableStateProvider("feed") {
                    FeedScreen(initialState(), null, {}, {}, {}, {}, {}, {}, {}, {})
                } else Text("帖子详情")
            }
        }
        compose.onAllNodes(hasScrollAction())[1].performScrollToIndex(12)
        compose.onNodeWithText("更新测试话题 13").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.onNodeWithText("帖子详情").assertIsDisplayed()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("更新测试话题 13").assertIsDisplayed()
        compose.onNodeWithText("更新测试话题 1").assertDoesNotExist()
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
