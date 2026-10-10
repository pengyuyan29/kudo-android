package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.ui.account.*
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AboutPageUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsKeepsShortSiteLinkTitleAndMovesAllAboutEntriesOut() {
        compose.setContent { KudoTheme(DisplaySettings()) { SettingsScreen(DisplaySettings(), {}, null, {}, {}, {}) } }
        compose.onNodeWithText("站内链接").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Linux.do 站内链接").assertDoesNotExist()
        for (label in listOf("关于酷DO", "开源致谢", "隐私说明", "社区指南")) compose.onNodeWithText(label).assertDoesNotExist()
        capture("settings-reorganized.png")
    }

    @Test fun aboutHasBothBrandsHeartBodyAndWorkingMovedEntries() {
        var link = ""
        compose.setContent { KudoTheme(DisplaySettings()) { AboutScreen(openLink = { link = it }) {} } }
        compose.onNodeWithContentDescription("酷DO Logo").assertIsDisplayed()
        compose.onNodeWithContentDescription("LINUX DO Logo").assertIsDisplayed()
        val left = compose.onNodeWithContentDescription("酷DO Logo").fetchSemanticsNode().boundsInRoot
        val heart = compose.onNodeWithContentDescription("热爱").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithContentDescription("LINUX DO Logo").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < heart.left && heart.right < right.left)
        assertEquals(left.center.y, right.center.y, 1f)
        compose.onNodeWithText("酷DO是独立 LINUX DO 第三方安卓原生客户端").assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        capture("about-two-brands.png")
        compose.onNodeWithText("社区指南").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("https://linux.do/guidelines", link) }
        compose.onNodeWithText("开源致谢").performScrollTo().performClick()
        compose.onNodeWithText("FluxDO 接口参考").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("https://github.com/Lingyan000/fluxdo", link) }
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("隐私说明").performScrollTo().performClick()
        compose.onNodeWithText("酷DO 直接连接 Linux.do。", substring = true).assertIsDisplayed()
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("${BuildConfig.VERSION_NAME} · LINUX DO 第三方客户端").performScrollTo().performClick()
        compose.onNodeWithText("酷DO ${BuildConfig.VERSION_NAME}", substring = true).assertIsDisplayed()
    }

    @Test fun darkNarrowLargeTextRemainsReadableAndScrollableInEveryMaterial() {
        var settings by mutableStateOf(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f))
        compose.setContent { KudoTheme(settings) {
            Box(Modifier.fillMaxSize(), propagateMinConstraints = false) {
                Box(Modifier.width(280.dp).height(420.dp).testTag("about-narrow")) { AboutScreen {} }
            }
        } }
        for (mode in GlassMode.entries) {
            compose.runOnIdle { settings = settings.copy(glass = mode) }
            compose.onNodeWithContentDescription("LINUX DO Logo").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("酷DO是独立 LINUX DO 第三方安卓原生客户端").performScrollTo().assertIsDisplayed()
            capture("about-dark-narrow-${mode.name.lowercase()}.png", "about-narrow")
            compose.onNodeWithText("隐私说明").performScrollTo().performClick()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("酷DO 直接连接 Linux.do。", substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
            assertTrue(layouts.single().layoutInput.style.color.red > 0.5f)
            compose.onNodeWithText("知道了").assertIsDisplayed().performClick()
            compose.onNodeWithText("真诚 · 友善 · 团结 · 专业", substring = false).assertExists()
        }
    }

    private fun capture(name: String, tag: String? = null) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = if (tag == null) instrumentation.uiAutomation.takeScreenshot() else compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
