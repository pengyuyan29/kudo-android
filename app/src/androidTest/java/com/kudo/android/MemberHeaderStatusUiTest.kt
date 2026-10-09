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
    @Test fun statusBarRestoresThemeWhenHeaderOnlyRemainsBelowItsIcons() {
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
        val height = compose.onNodeWithTag("member-header").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("member-profile").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, height - inset / 2f) }
        compose.waitForIdle()
        val remaining = compose.onNodeWithTag("member-header").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { assertNull("标题图已离开状态栏图标区域，应恢复主题图标颜色；inset=$inset, height=$height, remaining=$remaining", style) }
    }
}
