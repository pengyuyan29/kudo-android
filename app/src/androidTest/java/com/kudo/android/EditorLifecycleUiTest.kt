package com.kudo.android

import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.editor.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class EditorLifecycleUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var app: AppContainer
    private val editors = mutableListOf<EditorViewModel>()
    private val userId = Int.MIN_VALUE
    @Before fun create() { instrumentation.runOnMainSync { app = AppContainer(instrumentation.targetContext) } }
    @After fun clean() {
        instrumentation.runOnMainSync { editors.forEach { it.viewModelScope.cancel() }; listOf(8, 9).forEach { app.settings.clearDraft("${userId}_700_$it") } }
    }
    private fun editor(floor: Int = 8, rules: suspend () -> ReplyRules = { ReplyRules(16, 64000) }, publish: suspend (Draft) -> PublishResult = { PublishResult(700, false) }): EditorViewModel {
        lateinit var value: EditorViewModel
        instrumentation.runOnMainSync { value = EditorViewModel(app, userId, 700, floor, publish, rulesRequest = rules); editors += value }
        runBlocking { withTimeout(5000) { while (value.rulesLoading) delay(10) } }
        return value
    }

    @Test fun successfulReplyClearsBothStoredAndLiveDraftBeforeTheSameSheetReopens() = runBlocking {
        val vm = editor()
        instrumentation.runOnMainSync { vm.edit(Draft(raw = "这是达到网站最低字数要求的回复内容")); vm.publish() }
        withTimeout(5000) { vm.published.first() }
        instrumentation.runOnMainSync {
            assertEquals("", app.settings.draft("${userId}_700_8").raw)
            assertEquals("", vm.draft.raw)
            assertFalse(vm.sending)
        }
    }

    @Test fun tooShortReplyNeverPublishesAndKeepsItsDraft() = runBlocking {
        val writes = java.util.concurrent.atomic.AtomicInteger()
        val vm = editor { writes.incrementAndGet(); PublishResult(700, false) }
        instrumentation.runOnMainSync { vm.edit(Draft(raw = "短回复")); vm.publish() }
        withTimeout(5000) { while (vm.sending) delay(20) }
        assertEquals("不足网站最小字数时不能发送", 0, writes.get())
        assertEquals("短回复", app.settings.draft("${userId}_700_8").raw)
    }

    @Test fun closingAndReconstructingKeepsDraftsSeparatedByActualReplyFloor() {
        val eight = editor(8)
        val nine = editor(9)
        instrumentation.runOnMainSync { eight.edit(Draft(raw = "八楼草稿")); nine.edit(Draft(raw = "九楼草稿")) }
        val restoredEight = editor(8)
        val restoredNine = editor(9)
        assertEquals("八楼草稿", restoredEight.draft.raw)
        assertEquals("九楼草稿", restoredNine.draft.raw)
    }

    @Test fun failedPublishKeepsDraftAndRestoresEditing() = runBlocking {
        val vm = editor { throw IllegalStateException("夹具失败") }
        val raw = "这是发送失败之后必须保留下来的回复草稿"
        instrumentation.runOnMainSync { vm.edit(Draft(raw = raw)); vm.publish() }
        withTimeout(5000) { while (vm.sending) delay(20) }
        assertEquals(raw, vm.draft.raw)
        assertEquals(raw, app.settings.draft("${userId}_700_8").raw)
        assertEquals("夹具失败", vm.error)
    }

    @Test fun unavailableRulesPreventPublishingAndCanBeRetriedWithoutLosingText() = runBlocking {
        var ready = false
        val writes = java.util.concurrent.atomic.AtomicInteger()
        val vm = editor(rules = { if (!ready) error("配置夹具失败") else ReplyRules(4, 64000) }) { writes.incrementAndGet(); PublishResult(700, false) }
        instrumentation.runOnMainSync { vm.edit(Draft(raw = "四字回复")); vm.publish() }
        assertEquals(0, writes.get())
        assertEquals("四字回复", vm.draft.raw)
        instrumentation.runOnMainSync { ready = true; vm.reloadReplyRules() }
        withTimeout(5000) { while (vm.rulesLoading) delay(10) }
        instrumentation.runOnMainSync { vm.publish() }
        withTimeout(5000) { vm.published.first() }
        assertEquals(1, writes.get())
    }
}
