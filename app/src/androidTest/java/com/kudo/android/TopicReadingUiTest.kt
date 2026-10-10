package com.kudo.android

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.topic.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TopicReadingUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(2, "test", "离线作者", "")
    private fun post(number: Int, html: String = "<p>实际可见的正文</p>") = Post(number + 100, number, author, html, "", 0, false, null, null, false)

    @Test fun detailAndThreadSheetUseSeparateViewportsAndOnlyFullRepliesCount() {
        var active by mutableStateOf(true)
        val batches = mutableListOf<TopicReadingBatch>()
        val root = post(8)
        val child = post(9).copy(replyTo = 8)
        val detail = TopicDetail(12, "离线详情", 1, listOf(root, child), listOf(root.id, child.id), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicScreen(TopicState(detail, loading = false, viewerId = 7), GlassMode.TRANSLUCENT,
                {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, readingEnabled = active, onReadingBatch = { batches += it })
        } }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { active = false }
        compose.waitUntil(3000) { batches.isNotEmpty() }
        assertEquals(setOf(8), batches.flatMap { it.timings.keys }.toSet())
        compose.onNodeWithText("查看 1 条回复").performClick()
        compose.waitForIdle()
        compose.runOnIdle { active = true }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { active = false }
        compose.waitUntil(3000) { batches.any { 9 in it.timings } }
    }

    @Test fun disposingAnOldViewerKeepsItsOriginalSubmissionIdentity() {
        var viewer by mutableIntStateOf(7)
        val batches = mutableListOf<Pair<Int, TopicReadingBatch>>()
        compose.setContent { KudoTheme(DisplaySettings()) {
            val boundViewer = viewer
            TopicReadingEnvironment(12, boundViewer, true, { batches += boundViewer to it }) {
                PostReadingViewport { viewport ->
                    Column(Modifier.fillMaxSize().then(viewport)) {
                        PostReadingContent(if (boundViewer == 7) 8 else 9, true) {
                            androidx.compose.material3.Text("离线正文")
                        }
                    }
                }
            }
        } }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { viewer = 10 }
        compose.waitUntil(3000) { batches.isNotEmpty() }
        assertEquals(7, batches.first().first)
        assertEquals(setOf(8), batches.first().second.timings.keys)
    }

    @Test fun ignoredBodiesGuestsAndBlockingSheetsNeverProduceReading() {
        var viewer by mutableStateOf<Int?>(7)
        var ignored by mutableStateOf(setOf("test"))
        var blocked by mutableStateOf(false)
        val batches = mutableListOf<TopicReadingBatch>()
        val root = post(1)
        val detail = TopicDetail(12, "离线话题", 1, listOf(root), listOf(root.id), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            CompositionLocalProvider(LocalIgnoredUsers provides ignored, LocalReadingBlocked provides blocked) {
                TopicReadingEnvironment(12, viewer, true, { batches += it }) {
                    PostReadingViewport { viewport ->
                        LazyColumn(Modifier.fillMaxSize().then(viewport)) {
                            item { TopicPost(root, detail, TopicState(detail, loading = false), true, {}, {}, {}, {}, {}, {}, {}) }
                        }
                    }
                }
            }
        } }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { viewer = null; ignored = emptySet() }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { viewer = 7; blocked = true }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { assertTrue(batches.isEmpty()); blocked = false }
        compose.waitForIdle(); SystemClock.sleep(1300)
        compose.runOnIdle { blocked = true }
        compose.waitUntil(3000) { batches.isNotEmpty() }
        assertEquals(setOf(1), batches.flatMap { it.timings.keys }.toSet())
    }

    @Test fun actualBodyExposureProducesOnlyItsFloorAndPausesUnderOverlays() {
        var active by mutableStateOf(true)
        val batches = mutableListOf<TopicReadingBatch>()
        val root = post(8)
        val preview = post(9)
        val detail = TopicDetail(12, "离线话题", 1, listOf(root, preview), listOf(root.id, preview.id), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicReadingEnvironment(12, 7, active, { batches += it }) {
                PostReadingViewport { viewport ->
                    LazyColumn(Modifier.fillMaxWidth().height(350.dp).then(viewport)) {
                        item { TopicPost(root, detail, TopicState(detail, loading = false), true, {}, {}, {}, {}, {}, {}, {}) }
                        item { ReplyPreview(PostThread(root, listOf(preview)), null, false, true) {} }
                    }
                }
            }
        } }
        compose.waitForIdle()
        SystemClock.sleep(1600)
        compose.runOnIdle { active = false }
        compose.waitUntil(3000) { batches.isNotEmpty() }
        assertEquals(setOf(8), batches.flatMap { it.timings.keys }.toSet())
        val before = batches.size
        SystemClock.sleep(1600)
        compose.runOnIdle { assertEquals(before, batches.size) }
    }

    @Test fun longBodyIsNotReadFromItsHeaderButCountsWhenItsEndIsVisible() {
        var active by mutableStateOf(true)
        val batches = mutableListOf<TopicReadingBatch>()
        val root = post(1, (1..70).joinToString("") { "<p>长正文第 $it 段，需要滚动阅读。</p>" })
        val detail = TopicDetail(12, "长文", 1, listOf(root), listOf(root.id), false)
        compose.setContent { KudoTheme(DisplaySettings()) {
            TopicReadingEnvironment(12, 7, active, { batches += it }) {
                PostReadingViewport { viewport ->
                    LazyColumn(Modifier.fillMaxWidth().height(350.dp).then(viewport).testTag("reading-scroll")) {
                        item { TopicPost(root, detail, TopicState(detail, loading = false), true, {}, {}, {}, {}, {}, {}, {}) }
                    }
                }
            }
        } }
        compose.waitForIdle()
        SystemClock.sleep(1600)
        compose.runOnIdle { active = false }
        compose.waitForIdle()
        assertTrue("仅看到长正文开头不能标读", batches.isEmpty())
        compose.onNodeWithText("正文").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { active = true }
        compose.waitForIdle()
        SystemClock.sleep(1600)
        compose.runOnIdle { active = false }
        compose.waitUntil(3000) { batches.isNotEmpty() }
        assertEquals(setOf(1), batches.flatMap { it.timings.keys }.toSet())
    }
}
