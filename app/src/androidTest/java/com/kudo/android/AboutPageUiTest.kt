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
        compose.onNodeWithText(BuildConfig.VERSION_NAME, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("酷DO") and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText("${BuildConfig.VERSION_NAME} · LINUX DO 第三方客户端").assertDoesNotExist()
        compose.onNodeWithText("技术与设计参考").assertDoesNotExist()
        compose.onNodeWithText("账号与草稿仅保存在本机").assertDoesNotExist()
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
        compose.onNodeWithTag("about-content").performScrollToNode(hasTestTag("about-slogan"))
        compose.onNodeWithTag("about-slogan").assertIsDisplayed()
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
            compose.onNodeWithTag("about-content").performScrollToNode(hasContentDescription("LINUX DO Logo"))
            compose.onNodeWithContentDescription("LINUX DO Logo").assertIsDisplayed()
            compose.onNodeWithTag("about-content").performScrollToNode(hasText("酷DO是独立 LINUX DO 第三方安卓原生客户端"))
            compose.onNodeWithText("酷DO是独立 LINUX DO 第三方安卓原生客户端").assertIsDisplayed()
            capture("about-dark-narrow-${mode.name.lowercase()}.png", "about-narrow")
            compose.onNodeWithTag("about-content").performScrollToNode(hasText("隐私说明"))
            compose.onNodeWithText("隐私说明").performClick()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("酷DO 直接连接 Linux.do。", substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().isLineEllipsized(layouts.single().lineCount - 1))
            assertTrue(layouts.single().layoutInput.style.color.red > 0.5f)
            compose.onNodeWithText("知道了").assertIsDisplayed().performClick()
            compose.onNodeWithTag("about-content").performScrollToNode(hasTestTag("about-slogan"))
            capture("about-dark-narrow-${mode.name.lowercase()}-footer.png", "about-narrow")
            compose.onNodeWithTag("about-slogan").assertIsDisplayed()
        }
    }

    @Test fun narrowAboutRowsDoNotSqueezeTheirTitlesIntoIndividualCharacters() {
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            Box(Modifier.fillMaxSize(), propagateMinConstraints = false) {
                Box(Modifier.width(280.dp).height(420.dp)) { AboutScreen {} }
            }
        } }
        for (label in listOf("开源致谢", "社区指南", "隐私说明")) {
            compose.onNodeWithTag("about-content").performScrollToNode(hasText(label))
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("短标题应有完整单行宽度：$label", 1, layouts.single().lineCount)
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
