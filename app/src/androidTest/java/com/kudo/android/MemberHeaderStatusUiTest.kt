package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kudo.android.data.*
import com.kudo.android.ui.account.ProfileScreen
import com.kudo.android.ui.theme.KudoTheme
import org.junit.*
import org.junit.Assert.*

class MemberHeaderStatusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun statusBarKeepsHeaderStyleWhenCollapsedAndRestoresOnExit() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        var style: Boolean? = null
        var inset = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            inset = WindowInsets.statusBars.getTop(LocalDensity.current)
            Box(Modifier.fillMaxSize()) { Box(Modifier.height(480.dp)) {
                ProfileScreen(Member(42, "fixture", "资料测试", "", trustLevel = 3), {}, {}, {}, {}, statusBarStyle = { style = it })
            } }
        } }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(inset > 0); assertEquals(true, style) }
        compose.onNodeWithTag("member-profile").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("member-compact-header").assertIsDisplayed()
        compose.runOnIdle { assertEquals(true, style) }
    }
}
