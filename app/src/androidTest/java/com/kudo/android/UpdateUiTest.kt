package com.kudo.android

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.account.AboutScreen
import com.kudo.android.ui.theme.KudoTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateUiTest {
    @get:Rule val compose = createComposeRule()
    private val release = GitHubRelease("1.2.3", "酷DO 1.2.3", "## 新增\n- 支持检查更新\n\n## 修复\n- 头像角标完整显示", "2026-10-11", "$KudoSourceUrl/releases/tag/v1.2.3")

    @Test fun sourceUsesExternalCallbackAndUpdateShowsReleaseNotesAndDownload() {
        var external = ""
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            AboutScreen(openLink = { fail("源码不应打开应用内网页") }, openExternal = { external = it },
                checkUpdate = { requests++; release }) {}
        } }
        compose.onNodeWithText("查看源码").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(KudoSourceUrl, external); assertEquals(0, requests) }
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("发现新版本 1.2.3").assertIsDisplayed()
        compose.onNodeWithText("• 支持检查更新").assertIsDisplayed()
        compose.onNodeWithText("• 头像角标完整显示").assertIsDisplayed()
        compose.onNodeWithText("取消").assertDoesNotExist()
        compose.onNodeWithText("前往 GitHub 下载").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(release.url, external); assertEquals(1, requests) }
    }

    @Test fun missingReleaseCanRetryAndCurrentVersionStillDisplaysNotes() {
        var requests = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            AboutScreen(checkUpdate = {
                requests++
                if (requests == 1) throw UpdateFailure("暂无公开版本")
                release.copy(version = BuildConfig.VERSION_NAME, notes = "")
            }) {}
        } }
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("暂无公开版本").assertIsDisplayed()
        compose.onNodeWithText("当前已是最新版本").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("当前已是最新版本").assertIsDisplayed()
        compose.onNodeWithText("此版本暂未提供更新日志。").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun longChangelogInShortLargeTextWindowScrollsAndClosingCancelsCheck() {
        var cancelled = false
        compose.setContent { KudoTheme(DisplaySettings(fontScale = 1.3f)) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                AboutScreen(checkUpdate = { try { awaitCancellation() } finally { cancelled = true } }) {}
            }
        } }
        compose.onNodeWithTag("about-content").performScrollToNode(hasText("检查更新"))
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("正在检查 GitHub 版本…").assertIsDisplayed()
        compose.onNodeWithTag("modal-scrim").performTouchInput { click(Offset(2f, 2f)) }
        compose.onNodeWithTag("liquid-action-sheet").assertDoesNotExist()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun longNotesRemainScrollableUntilDownload() {
        compose.setContent { KudoTheme(DisplaySettings(fontScale = 1.3f)) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                AboutScreen(checkUpdate = { release.copy(notes = (1..60).joinToString("\n") { "- 更新内容 $it" }) }) {}
            }
        } }
        compose.onNodeWithTag("about-content").performScrollToNode(hasText("检查更新"))
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("• 更新内容 60").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("前往 GitHub 下载").performScrollTo().assertIsDisplayed()
    }
}
