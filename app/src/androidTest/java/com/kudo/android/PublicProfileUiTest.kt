package com.kudo.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.*
import com.kudo.android.data.*
import com.kudo.android.ui.account.*
import com.kudo.android.ui.components.ErrorState
import com.kudo.android.ui.theme.KudoTheme
import org.junit.*
import org.junit.Assert.*
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Collections

@OptIn(coil3.annotation.DelicateCoilApi::class)
class PublicProfileUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private val fetched = Collections.synchronizedList(mutableListOf<String>())
    private val member = Member(1, "fixture", "个人主页测试", "https://profile.test/avatar.png", trustLevel = 3,
        bio = "真诚、友善，一起探索有趣的技术。", canFollow = true, profileBackground = "https://profile.test/wrong-profile.png")
    private val profile = PublicProfile(member, 15, 8, "2026-10-10T10:00:00Z", "2024-01-01T00:00:00Z", "", "", "成员", true)
    private val summary = ProfileSummary(mapOf("likes_received" to 2394, "days_visited" to 77, "topic_count" to 12, "post_count" to 58,
        "posts_read_count" to 3700), listOf(ProfileEntry("top", 9, null, "热门主题")),
        listOf(ProfileEntry("reply", 9, 8, "热门回复原楼层")), emptyList(), emptyMap(), emptyList(), emptyList())
    private val feeds = ProfileTab.entries.filter { it != ProfileTab.SUMMARY }.associateWith { tab ->
        ProfileFeedState(listOf(ProfileEntry("${tab.name}-8", 9, 8, "${tab.label}记录", "内容摘要")), loaded = true)
    }
    @Before fun images() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://profile.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch(): FetchResult {
                            val url = data.toString(); fetched.add(url)
                            if (url.endsWith("bad.png")) throw IOException("offline failed image")
                            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(if (url.endsWith("card.png")) 0xFF235C63.toInt() else 0xFFA66635.toInt())
                            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                            bitmap.recycle()
                            return SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY)
                        }
                    }
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
    }
    @After fun restore() { SingletonImageLoader.setUnsafe(previous); loader.shutdown() }
    private fun show(card: String = "https://profile.test/card.png", dark: Boolean = false, scale: Float = 1f,
        opened: (String) -> Unit = {}, status: (Boolean?) -> Unit = {}) {
        compose.setContent { KudoTheme(DisplaySettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, fontScale = scale)) {
            PublicProfilePage(PublicProfileState(profile, loading = false, cardBackground = card, cardLoading = false,
                summary = summary, summaryLoading = false, feeds = feeds), 2, {}, {}, opened, {}, {}, {}, statusBarStyle = status)
        } }
        compose.waitUntil(5000) { fetched.isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun cardBackgroundReplacesProfileTitleImageAndHeaderIsCompact() {
        show()
        compose.onNodeWithText("个人主页测试").assertIsDisplayed()
        compose.onNodeWithText("2394").assertIsDisplayed()
        compose.onNodeWithTag("profile-follow").assertIsDisplayed()
        compose.runOnIdle { assertTrue(fetched.any { it.endsWith("card.png") }); assertFalse(fetched.any { it.endsWith("wrong-profile.png") }) }
        screenshot("public-profile-light.png")
    }
    @Test fun failedCardFallsBackToAvatarPixels() {
        show("https://profile.test/bad.png")
        compose.waitUntil(5000) { fetched.any { it.endsWith("avatar.png") } }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("public-profile-background").captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, (pixels.height * .1f).toInt()]
        assertTrue("失败背景应显示头像暖色像素", color.red > color.blue * 1.8f)
        screenshot("public-profile-avatar-fallback.png")
    }
    @Test fun clickingAndSwipingTabsKeepOriginalReplyFloor() {
        var url = ""
        show(opened = { url = it })
        compose.onNodeWithTag("profile-tab-REPLIES").performClick()
        compose.onNodeWithTag("profile-tab-REPLIES").assertIsSelected()
        compose.onNodeWithText("回复记录").performClick()
        compose.runOnIdle { assertTrue(url.endsWith("/9/8")) }
        compose.onNodeWithTag("profile-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("profile-tab-LIKES").assertIsSelected()
        compose.onNodeWithText("赞记录").assertIsDisplayed()
    }
    @Test fun refreshingWhileViewingRepliesReloadsTheVisibleTab() {
        val requested = mutableListOf<ProfileTab>()
        var state by mutableStateOf(PublicProfileState(profile, loading = false, cardLoading = false,
            summary = summary, summaryLoading = false, feeds = feeds))
        compose.setContent { KudoTheme(DisplaySettings()) {
            PublicProfilePage(state, 2, {}, {}, {}, {}, {}, {}, load = { tab, _, _ -> requested.add(tab) })
        } }
        compose.onNodeWithTag("profile-tab-REPLIES").performClick()
        compose.onNodeWithText("回复记录").assertIsDisplayed()
        compose.runOnIdle {
            requested.clear()
            state = state.copy(feeds = emptyMap())
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("刷新后当前回复标签必须重新读取，不能永远停在加载中", requested.contains(ProfileTab.REPLIES)) }
    }
    @Test fun scrollingPinsTabsAndRestoresStatusBarStyle() {
        var style: Boolean? = null
        show(status = { style = it })
        compose.runOnIdle { assertEquals(false, style) }
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.runOnIdle { assertNull(style) }
        compose.onNodeWithTag("profile-tabs").assertIsDisplayed()
        val tabs = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot
        assertTrue("吸顶标签应位于屏幕上方", tabs.top < 300f)
        screenshot("public-profile-collapsed.png")
    }
    @Test fun darkLargeTextAndDraggableMoreSheetRemainUsable() {
        show(dark = true, scale = 1.3f)
        compose.onNodeWithContentDescription("个人主页更多操作").performClick()
        compose.onNodeWithText("复制主页链接").assertIsDisplayed()
        screenshot("public-profile-dark-large-sheet.png")
        compose.onNodeWithTag("liquid-action-sheet").performTouchInput { swipeDown() }
        compose.onNodeWithText("复制主页链接").assertDoesNotExist()
    }
    @Test fun unavailableContentHasReturnAndNoMisleadingVerificationOrRetry() {
        var returned = false
        compose.setContent { KudoTheme(DisplaySettings()) { ErrorState(ApiFailure(FailureKind.NOT_FOUND, "not found"), {}, {}, { returned = true }) } }
        compose.onNodeWithText("内容不存在或暂无权限").assertIsDisplayed()
        compose.onNodeWithText("完成网页验证").assertDoesNotExist()
        compose.onNodeWithText("重新加载").assertDoesNotExist()
        compose.onNodeWithText("返回上一页").performClick()
        compose.runOnIdle { assertTrue(returned) }
    }
    @Test fun narrowLargeTextRetainsScrollableBodyAndAnonymousActionsRequestLogin() {
        var logins = 0
        compose.setContent { KudoTheme(DisplaySettings(fontScale = 1.3f)) { Box(Modifier.width(280.dp).fillMaxHeight()) {
            PublicProfilePage(PublicProfileState(profile.copy(member = member.copy(bio = "很长的个人简介".repeat(20))), loading = false,
                cardLoading = false, summary = summary, summaryLoading = false, feeds = feeds), null, {}, { logins++ }, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("profile-follow").performClick()
        compose.runOnIdle { assertEquals(1, logins) }
        compose.onNodeWithTag("profile-pager").assertIsDisplayed()
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.onNodeWithText("热门主题").assertIsDisplayed()
        screenshot("public-profile-narrow-large.png")
    }
}
