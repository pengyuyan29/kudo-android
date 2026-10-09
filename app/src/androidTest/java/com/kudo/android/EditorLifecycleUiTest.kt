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
    private fun editor(floor: Int = 8, publish: suspend (Draft) -> PublishResult = { PublishResult(700, false) }): EditorViewModel {
        lateinit var value: EditorViewModel
        instrumentation.runOnMainSync { value = EditorViewModel(app, userId, 700, floor, publish); editors += value }
        return value
    }

    @Test fun successfulReplyClearsBothStoredAndLiveDraftBeforeTheSameSheetReopens() = runBlocking {
        val vm = editor()
        instrumentation.runOnMainSync { vm.edit(Draft(raw = "已发送内容")); vm.publish() }
        withTimeout(5000) { vm.published.first() }
        instrumentation.runOnMainSync {
            assertEquals("", app.settings.draft("${userId}_700_8").raw)
            assertEquals("", vm.draft.raw)
            assertFalse(vm.sending)
        }
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
        instrumentation.runOnMainSync { vm.edit(Draft(raw = "失败后保留")); vm.publish() }
        withTimeout(5000) { while (vm.sending) delay(20) }
        assertEquals("失败后保留", vm.draft.raw)
        assertEquals("失败后保留", app.settings.draft("${userId}_700_8").raw)
        assertEquals("夹具失败", vm.error)
    }
}
