package com.kudo.android

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.*
import com.kudo.android.ui.account.ProfileScreen
import com.kudo.android.ui.theme.KudoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import coil3.*
import coil3.fetch.*
import coil3.request.Options
import coil3.decode.*
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import okio.Buffer
import java.io.ByteArrayOutputStream

class MemberHeaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previous: ImageLoader
    private lateinit var images: ImageLoader
    @Before fun images() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(20, 180, 60)) }
        for (x in 0..39) for (y in 0..19) bitmap.setPixel(x, y, android.graphics.Color.rgb(220, 40, 20))
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        images = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://member-image.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch() = SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY)
                    }
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(images)
    }
    @After fun restore() { SingletonImageLoader.setUnsafe(previous); images.shutdown() }
    @Test fun accountStatusBadgeExtendsOutsideFaceWithoutBeingClipped() {
        var loaded = false
        compose.setContent { KudoTheme(DisplaySettings()) {
            Box(Modifier.width(360.dp).height(700.dp)) {
                ProfileScreen(Member(42, "fixture", "角标测试", "", status = UserStatus("", "在线"),
                    profileBackground = "https://member-image.test/header"), {}, {}, {}, {}, statusBarStyle = { loaded = it == false })
            }
        } }
        compose.waitUntil(5000) { loaded }
        val parent = compose.onNodeWithTag("member-header").fetchSemanticsNode().boundsInRoot
        val face = compose.onNodeWithTag("member-avatar").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onNodeWithTag("member-header").captureToImage().toPixelMap()
        var outsideWhite = 0
        for (x in (face.right - parent.left).toInt() until (face.right - parent.left + 10).toInt().coerceAtMost(pixels.width)) {
            for (y in (face.bottom - parent.top - 40).toInt().coerceAtLeast(0) until (face.bottom - parent.top).toInt().coerceAtMost(pixels.height)) {
                val c = pixels[x, y]
                if (c.red > .97f && c.green > .97f && c.blue > .97f) outsideWhite++
            }
        }
        assertTrue("状态角标应绘制到头像外，实际白色像素=$outsideWhite", outsideWhite > 10)
    }
    @Test fun squareHeaderCollapsesFromListAndKeepsIdentityAndProfileArrow() {
        var profiles = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            Box(Modifier.width(360.dp).height(700.dp)) {
                ProfileScreen(Member(42, "fixture", "方形顶部", "", trustLevel = 3), {}, {}, {}, { profiles++ })
            }
        } }
        val header = compose.onNodeWithTag("member-header-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(header.width, header.height, 2f)
        val nickname = compose.onNodeWithText("方形顶部", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val level = compose.onNodeWithText("Lv.3").fetchSemanticsNode().boundsInRoot
        assertTrue(level.left >= nickname.right)
        assertEquals(nickname.center.y, level.center.y, 3f)
        compose.onNodeWithTag("member-avatar").performClick()
        compose.onNodeWithTag("member-profile-arrow").performClick()
        compose.onNodeWithTag("member-profile").performTouchInput { swipeUp() }
        compose.onNodeWithTag("member-compact-header").assertIsDisplayed()
        compose.onNodeWithText("方形顶部").assertIsDisplayed()
        compose.onNodeWithTag("member-profile-arrow").performClick()
        compose.runOnIdle { assertEquals(3, profiles) }
        compose.onNodeWithTag("member-profile").performTouchInput { swipeDown(); swipeDown() }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("member-header-viewport").fetchSemanticsNode().boundsInRoot.height > header.width * .9f)
    }
    @Test fun wholeIdentityOpensProfileButLevelRemainsIndependent() {
        var profiles = 0
        var levels = 0
        compose.setContent { KudoTheme(DisplaySettings()) {
            Box(Modifier.width(360.dp).height(700.dp)) {
                ProfileScreen(Member(42, "fixture", "身份整排", "", trustLevel = 3), {}, {}, {}, { profiles++ },
                    destination = { if (it == AccountDestination.TRUST) levels++ })
            }
        } }
        val header = compose.onNodeWithTag("member-header").fetchSemanticsNode().boundsInRoot
        val identity = compose.onNodeWithTag("member-identity").fetchSemanticsNode().boundsInRoot
        assertTrue("身份行应高于底部统计", header.bottom - identity.bottom > identity.height * 1.5f)
        compose.onNodeWithTag("member-identity").performClick()
        compose.onNodeWithText("Lv.3").performClick()
        compose.runOnIdle { assertEquals(1, profiles); assertEquals(1, levels) }
        compose.onNodeWithTag("member-profile").performTouchInput { swipeUp() }
        compose.onNodeWithTag("member-compact-header").performClick()
        compose.onNodeWithText("Lv.3").performClick()
        compose.runOnIdle { assertEquals(2, profiles); assertEquals(2, levels) }
    }
    @Test fun collapseKeepsOriginalImageBottomWithOnlySlightlyDarkerMask() {
        var loaded = false
        compose.setContent { KudoTheme(DisplaySettings()) {
            Box(Modifier.width(360.dp).height(700.dp)) {
                ProfileScreen(Member(42, "fixture", "图片底部", "https://member-image.test/avatar"), {}, {}, {}, {}, statusBarStyle = { loaded = it == false })
            }
        } }
        compose.waitUntil(5000) { loaded }
        val full = compose.onNodeWithTag("member-header-viewport").captureToImage().toPixelMap()
        val upper = full[2, full.height / 4]
        val lower = full[2, full.height - 5]
        assertTrue(upper.red > upper.green * 2)
        assertTrue(lower.green > lower.red * 2)
        compose.onNodeWithTag("member-profile").performTouchInput { swipeUp() }
        compose.onNodeWithTag("member-compact-header").assertIsDisplayed()
        val compact = compose.onNodeWithTag("member-header-viewport").captureToImage().toPixelMap()
        val bottom = compact[2, compact.height - 5]
        assertTrue("吸顶应显示原图绿色底部", bottom.green > bottom.red * 2)
        assertTrue("遮罩只略微加深", lower.green - bottom.green in .03f.. .13f)
    }
    @Test fun missingImageUsesWhiteBaseAndDarkStatusIcons() {
        var style: Boolean? = null
        compose.setContent { KudoTheme(DisplaySettings()) {
            Box(Modifier.width(360.dp).height(700.dp)) {
                ProfileScreen(Member(42, "fixture", "白底", ""), {}, {}, {}, {}, statusBarStyle = { style = it })
            }
        } }
        val full = compose.onNodeWithTag("member-header-viewport").captureToImage().toPixelMap()[2, 2]
        assertTrue(full.red > .98f && full.green > .98f && full.blue > .98f)
        compose.runOnIdle { assertEquals(true, style) }
    }
}
