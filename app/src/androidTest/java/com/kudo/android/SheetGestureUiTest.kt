package com.kudo.android

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kudo.android.data.*
import com.kudo.android.ui.account.SettingsScreen
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SheetGestureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sheetTracksFingerReturnsFromSmallDragAndDismissesWithoutChangingSettings() {
        var updates = 0
        compose.setContent { KudoTheme(DisplaySettings()) { SettingsScreen(DisplaySettings(), { updates++ }, null, {}, {}, {}) } }
        compose.onNodeWithText("主题风格").performClick()
        val handle = compose.onNodeWithTag("sheet-drag-handle").assertIsDisplayed()
        val sheet = compose.onNodeWithTag("liquid-action-sheet")
        val top = sheet.fetchSemanticsNode().boundsInRoot.top
        handle.performTouchInput { down(center); moveBy(Offset(0f, 100f)); advanceEventTime(300) }
        assertTrue(sheet.fetchSemanticsNode().boundsInRoot.top > top + 10f)
        handle.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(top, sheet.fetchSemanticsNode().boundsInRoot.top, 2f)
        handle.performTouchInput { swipe(center, center + Offset(0f, 550f), 600) }
        sheet.assertDoesNotExist()
        compose.onNodeWithText("主题风格").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, updates) }
    }
}
