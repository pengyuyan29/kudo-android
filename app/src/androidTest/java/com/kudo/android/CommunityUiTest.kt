package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.components.GlassNavigation
import com.kudo.android.ui.feed.FeedScreen
import com.kudo.android.ui.feed.FeedState
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CommunityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun darkThemeDefaultTextHasReadableForeground() {
        compose.setContent {
            KudoTheme(DisplaySettings(theme = ThemeMode.DARK)) {
                Text("深色模式默认文字", Modifier.testTag("default-text"))
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("default-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("深色页面的默认文字不能继承黑色", layouts.single().layoutInput.style.color.luminance() > 0.5f)
    }

    @Test fun glassNavigationSwitchesTabsAndDoesNotPublishOnDrag() {
        var selected by mutableIntStateOf(0)
        var published = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background))
                    GlassNavigation(selected, { selected = it }, { published++ }, backdrop, GlassMode.LIQUID, Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        compose.onNodeWithText("分类").performClick()
        compose.runOnIdle { assertEquals(1, selected); assertEquals(0, published) }
        compose.onNodeWithText("我的").performClick()
        compose.runOnIdle { assertEquals(4, selected) }
        compose.onNodeWithTag("navigation").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, published) }
        compose.onNodeWithContentDescription("发布话题").performClick()
        compose.runOnIdle { assertEquals(1, published) }
    }

    @Test fun themeAndMaterialOptionsChangeImmediately() {
        var settings by mutableStateOf(DisplaySettings())
        compose.setContent { KudoTheme(settings) { SettingsScreen(settings, { settings = it }, null, {}, {}, {}) } }
        compose.onNodeWithText("界面材质效果").performClick()
        compose.onNodeWithText("半透明").performClick()
        compose.runOnIdle { assertEquals(GlassMode.TRANSLUCENT, settings.glass) }
        compose.onNodeWithText("主题风格").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.DARK, settings.theme) }
        compose.onNodeWithText("字体大小").performClick()
        compose.onNodeWithText("大号 · 130%").performClick()
        compose.runOnIdle { assertEquals(1.3f, settings.fontScale) }
        capture("settings-dark-large.png")
    }

    @Test fun glassSelectionContinuesFromReleasePosition() {
        var selected by mutableIntStateOf(0)
        var published = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background))
                    GlassNavigation(selected, { selected = it }, { published++ }, backdrop, GlassMode.LIQUID,
                        Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val navigation = compose.onNodeWithTag("navigation")
        navigation.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.9f, centerY), delayMillis = 300)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val before = compose.onNodeWithTag("navigation-selection").getUnclippedBoundsInRoot()
        navigation.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val after = compose.onNodeWithTag("navigation-selection").getUnclippedBoundsInRoot()
        assertTrue("松手后胶囊不能跳回旧标签：${before.left} → ${after.left}", after.left >= before.left - (before.right - before.left) * 0.25f)
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals(4, selected); assertEquals(0, published) }
        val settled = compose.onNodeWithTag("navigation-selection").getUnclippedBoundsInRoot()
        navigation.performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(width * 0.5f, centerY), delayMillis = 300)
            up()
        }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals("松手于发布入口时保留原标签", 4, selected); assertEquals(0, published) }
        val returned = compose.onNodeWithTag("navigation-selection").getUnclippedBoundsInRoot()
        assertEquals("胶囊回到原标签", settled.left.value, returned.left.value, 1f)
        navigation.performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(width * 0.6f, centerY), delayMillis = 300)
            cancel()
        }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals(4, selected); assertEquals(0, published) }
        val cancelled = compose.onNodeWithTag("navigation-selection").getUnclippedBoundsInRoot()
        assertEquals("取消拖动回到原标签", settled.left.value, cancelled.left.value, 1f)
    }

    @Test fun draggingHighlightsCurrentAndPreviewWithoutSwitchingPage() {
        var selected by mutableIntStateOf(0)
        var published = 0
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.LIGHT))
        compose.setContent {
            KudoTheme(settings) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.surface))
                    GlassNavigation(selected, { selected = it }, { published++ }, backdrop, GlassMode.LIQUID,
                        Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().testTag("navigation"))
                }
            }
        }
        fun textColor(label: String): androidx.compose.ui.graphics.Color {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.color
        }
        val active = textColor("首页")
        val inactive = textColor("分类")
        assertNotEquals(active, inactive)
        val navigation = compose.onNodeWithTag("navigation")
        navigation.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.3f, centerY), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals("拖到分类时与当前首页同时高亮", active, textColor("分类"))
        assertEquals(active, textColor("首页"))
        assertEquals(inactive, textColor("我的"))
        compose.runOnIdle { assertEquals("松手之前不切换页面", 0, selected); assertEquals(0, published) }
        capture("navigation-drag-clear.png")
        navigation.performTouchInput { moveTo(Offset(width * 0.5f, centerY), delayMillis = 300) }
        compose.waitForIdle()
        assertEquals(active, textColor("首页"))
        assertEquals(inactive, textColor("分类"))
        compose.runOnIdle { assertEquals(0, selected); assertEquals(0, published) }
        navigation.performTouchInput { moveTo(Offset(width * 0.7f, centerY), delayMillis = 300) }
        compose.waitForIdle()
        assertEquals(active, textColor("消息"))
        assertEquals(inactive, textColor("分类"))
        assertEquals(active, textColor("首页"))
        navigation.performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, selected); assertEquals(0, published) }
        assertEquals(inactive, textColor("首页"))
        assertEquals(active, textColor("消息"))
        navigation.performTouchInput {
            down(Offset(width * 0.7f, centerY))
            moveTo(Offset(width * 0.3f, centerY), delayMillis = 300)
            cancel()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, selected); assertEquals(0, published) }
        assertEquals(inactive, textColor("分类"))
        assertEquals(active, textColor("消息"))
        compose.runOnIdle { settings = DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f) }
        compose.waitForIdle()
        val darkActive = textColor("消息")
        val darkInactive = textColor("首页")
        assertNotEquals(darkActive, darkInactive)
        navigation.performTouchInput {
            down(Offset(width * 0.7f, centerY))
            moveTo(Offset(width * 0.1f, centerY), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(darkActive, textColor("首页"))
        assertEquals(darkActive, textColor("消息"))
        assertEquals(darkInactive, textColor("分类"))
        capture("navigation-drag-dark-large.png")
        navigation.performTouchInput { cancel() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, selected); assertEquals(0, published) }
    }

    @Test fun nativePostRendersAndActionsAreWired() {
        val member = Member(1, "fixture", "界面测试", "")
        val post = Post(10, 1, member, "<p>这是一段用于仪器测试的原生正文。</p><blockquote><p>引用内容</p></blockquote><pre><code>val kudo = true</code></pre>", "2026-10-07T00:00:00Z", 12, false, null, null, true)
        var likes = 0
        var replies = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                TopicScreen(TopicState(TopicDetail(5, "原生阅读界面测试", 1, listOf(post), listOf(10), false), false), GlassMode.LIQUID,
                    {}, {}, {}, {}, { likes++ }, {}, { replies++ }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("原生阅读界面测试").assertIsDisplayed()
        compose.onNodeWithText("这是一段用于仪器测试的原生正文。").assertIsDisplayed()
        compose.onNodeWithText("val kudo = true").assertIsDisplayed()
        compose.onNodeWithContentDescription("赞主帖").performClick()
        compose.onNodeWithText("写回复").performClick()
        compose.runOnIdle { assertEquals(1, likes); assertEquals(1, replies) }
        capture("topic-native.png")
    }

    @Test fun feedRendersTestContentUnderGlassAndSelectsTopic() {
        val member = Member(1, "fixture", "界面测试", "")
        val topics = (1..6).map { Topic(it, "测试话题 $it：让阅读更专注", "原生界面的自动化测试内容，验证信息流、文本排版与液态玻璃导航。", member, 1, listOf("Android", "设计"), 23, 1200, 86, "", "2026-10-07T00:00:00Z") }
        var opened = 0
        compose.setContent {
            KudoTheme(DisplaySettings()) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        FeedScreen(FeedState(topics, listOf(Category(1, "开发调优", "dev", "FFB003", "", 6)), loading = false), member, {}, {}, {}, { opened = it }, {}, {}, {}, {})
                    }
                    GlassNavigation(0, {}, {}, backdrop, GlassMode.LIQUID, Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth())
                }
            }
        }
        compose.onNodeWithText("测试话题 1：让阅读更专注").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        capture("feed-test-content.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idling does not wait for the platform Dialog window exit animation.
        android.os.SystemClock.sleep(400)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
