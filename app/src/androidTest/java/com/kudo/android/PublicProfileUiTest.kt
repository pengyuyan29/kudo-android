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
                            bitmap.eraseColor(0xFFA66635.toInt())
                            if (url.endsWith("card.png")) for (y in 0 until 80) for (x in 0 until 80) {
                                bitmap.setPixel(x, y, if (y < 60) 0xFF3874A0.toInt() else 0xFF4B9477.toInt())
                            }
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
        compose.setContent {
            var icons by remember { mutableStateOf<Boolean?>(null) }
            KudoTheme(DisplaySettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, fontScale = scale), darkStatusIcons = icons) {
            PublicProfilePage(PublicProfileState(profile, loading = false, cardBackground = card, cardLoading = false,
                summary = summary, summaryLoading = false, feeds = feeds), 2, {}, {}, opened, {}, {}, {}, statusBarStyle = { icons = it; status(it) })
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
    @Test fun expandedHeaderIsSquareAndCollapsedToolbarKeepsImageAndActions() {
        var style: Boolean? = null
        show(status = { style = it })
        val header = compose.onNodeWithTag("public-profile-header").fetchSemanticsNode().boundsInRoot
        assertEquals("展开顶部为1:1", header.width, header.height, 2f)
        val expanded = compose.onRoot().captureToImage().toPixelMap()
        val expandedBottom = expanded[expanded.width - 5, header.bottom.toInt() - 8]
        repeat(2) { compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("profile-toolbar-avatar", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("profile-toolbar-follow").assertIsDisplayed()
        compose.onNodeWithContentDescription("个人主页更多操作").assertIsDisplayed()
        compose.onNodeWithText("2394").assertDoesNotExist()
        val tabs = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val bottom = pixels[pixels.width - 5, tabs.top.toInt() - 8]
        assertTrue("收起后仍是原图底部绿色区域", bottom.green > bottom.blue * 1.1f)
        assertTrue("遮罩只稍微更不透明", bottom.green in (expandedBottom.green * .65f)..(expandedBottom.green * .99f))
        compose.runOnIdle { assertEquals(false, style) }
        compose.runOnIdle { assertFalse(androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,
            compose.activity.window.decorView).isAppearanceLightStatusBars) }
        screenshot("public-profile-square-collapsed.png")
    }
    @Test fun toolbarIdentitySlotsExistBeforeFullyCollapsedForGradualReveal() {
        show()
        compose.onNodeWithTag("profile-toolbar-avatar", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("profile-toolbar-follow", useUnmergedTree = true).assertExists()
        repeat(1) { compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("profile-toolbar-avatar", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("profile-toolbar-follow", useUnmergedTree = true).assertExists()
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
    @Test fun scrollingPinsTabsAndKeepsWhiteStatusBarForeground() {
        var style: Boolean? = null
        show(status = { style = it })
        compose.runOnIdle { assertEquals(false, style) }
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(false, style) }
        compose.onNodeWithTag("profile-tabs").assertIsDisplayed()
        val tabs = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot
        assertTrue("吸顶标签应位于屏幕上方", tabs.top < 300f)
        screenshot("public-profile-collapsed.png")
    }
    @Test fun shortPageDownwardSwipeExpandsTheHeader() {
        show()
        val expandedTop = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top
        repeat(2) { compose.onNodeWithTag("public-profile-header").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("profile-tab-REPLIES").performClick()
        repeat(2) { compose.onNodeWithTag("profile-list-REPLIES").performTouchInput { swipeDown(durationMillis = 600) } }
        val restoredTop = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top
        assertEquals("短列表下拉的剩余距离必须交给顶部", expandedTop, restoredTop, 2f)
    }
    @Test fun upwardFlingFromPagerContinuesCollapsingTheHeader() {
        show()
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput {
            swipe(center.copy(y = height * .85f), center.copy(y = height * .6f), durationMillis = 80)
        }
        val tabs = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot
        assertTrue("抬手后的惯性仍需先收起顶部，实际标签顶部 ${tabs.top}", tabs.top < 300f)
    }
    @Test fun longListConsumesScrollOnlyAfterHeaderAndReturnsBeforeExpanding() {
        val entries = (1..40).map { ProfileEntry("row-$it", it, null, "列表记录 $it") }
        compose.setContent { KudoTheme(DisplaySettings()) {
            PublicProfilePage(PublicProfileState(profile, loading = false, cardLoading = false,
                summary = summary, summaryLoading = false,
                feeds = feeds + (ProfileTab.REPLIES to ProfileFeedState(entries, loaded = true))), 2, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithTag("profile-tab-REPLIES").performClick()
        val initialTop = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("profile-list-REPLIES").performTouchInput {
            swipe(center, center.copy(y = center.y - 90f), durationMillis = 1000)
        }
        val partialTop = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top
        assertTrue("先收顶部", partialTop < initialTop - 50f)
        compose.onNodeWithTag("profile-entry-row-1").assertIsDisplayed()
        repeat(4) { compose.onNodeWithTag("profile-list-REPLIES").performTouchInput { swipeUp(durationMillis = 600) } }
        val pinnedTop = compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("profile-entry-row-1").assertIsNotDisplayed()
        compose.onNodeWithTag("profile-list-REPLIES").performTouchInput {
            swipe(center, center.copy(y = center.y + 90f), durationMillis = 1000)
        }
        assertEquals("列表未回顶时不能展开顶部", pinnedTop,
            compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top, 2f)
        repeat(10) { compose.onNodeWithTag("profile-list-REPLIES").performTouchInput { swipeDown(durationMillis = 600) } }
        assertEquals(initialTop, compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot.top, 2f)
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
        compose.setContent { KudoTheme(DisplaySettings(fontScale = 1.3f), darkStatusIcons = false) { Box(Modifier.width(280.dp).fillMaxHeight()) {
            PublicProfilePage(PublicProfileState(profile.copy(member = member.copy(bio = "很长的个人简介".repeat(20))), loading = false,
                cardLoading = false, summary = summary, summaryLoading = false, feeds = feeds), null, {}, { logins++ }, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("profile-follow").performClick()
        compose.runOnIdle { assertEquals(1, logins) }
        compose.onNodeWithContentDescription("查看完整个人资料").performClick()
        compose.onNodeWithText("很长的个人简介".repeat(20)).assertIsDisplayed()
        compose.onNodeWithTag("liquid-action-sheet").performTouchInput { swipeDown() }
        compose.onNodeWithTag("profile-pager").assertIsDisplayed()
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() }
        compose.onNodeWithText("热门主题").assertIsDisplayed()
        screenshot("public-profile-narrow-large.png")
    }
    @Test fun collapsedFollowPreservesPermissionsBusyStateAndCallbacks() {
        var current by mutableStateOf(profile)
        var viewer by mutableStateOf<Int?>(null)
        var busy by mutableStateOf(false)
        var logins = 0
        var follows = 0
        var style: Boolean? = null
        var visible by mutableStateOf(true)
        compose.setContent { KudoTheme(DisplaySettings(fontScale = 1.3f)) { Box(Modifier.width(280.dp).fillMaxHeight()) {
            if (visible) PublicProfilePage(PublicProfileState(current, loading = false, cardLoading = false,
                summary = summary, summaryLoading = false, feeds = feeds, followBusy = busy), viewer,
                {}, { logins++ }, {}, {}, {}, {}, statusBarStyle = { style = it },
                follow = { follows++; current = current.copy(member = current.member.copy(isFollowed = true)) })
        } } }
        repeat(2) { compose.onNodeWithTag("profile-list-SUMMARY").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("profile-toolbar-follow").performClick()
        compose.runOnIdle { assertEquals(1, logins); viewer = 2 }
        compose.onNodeWithTag("profile-toolbar-follow").performClick()
        compose.onNodeWithTag("profile-toolbar-follow").assertTextEquals("已关注")
        compose.runOnIdle { assertEquals(1, follows); busy = true }
        compose.onNodeWithTag("profile-toolbar-follow").assertIsNotEnabled()
        compose.runOnIdle { busy = false; viewer = member.id }
        compose.onNodeWithTag("profile-toolbar-follow").assertDoesNotExist()
        compose.runOnIdle { viewer = 2; current = profile.copy(member = member.copy(canFollow = false)) }
        compose.onNodeWithTag("profile-toolbar-follow").assertDoesNotExist()
        compose.runOnIdle { visible = false }
        compose.runOnIdle { assertNull(style) }
    }
}
