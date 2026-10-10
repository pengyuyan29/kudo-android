package com.kudo.android

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.Options
import com.kudo.android.data.*
import com.kudo.android.ui.account.BadgePage
import com.kudo.android.ui.components.BadgeIcons
import com.kudo.android.ui.theme.KudoTheme
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class BadgeGalleryUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private fun badge(id: Int, earned: Boolean = false, image: Boolean = false) = UserBadge(id,
        listOf("欢迎", "自传作者", "编辑者", "第一个赞", "首次分享", "第一个表情符号", "首次引用")[id - 1],
        "收到一个赞", "<p>收到社区成员的一个赞即可获得。</p><p><a href=\"https://linux.do/guidelines\">社区指南</a></p>",
        if (id == 1) "heart" else "user", if (image) "https://badge.test/$id.png" else null,
        3, 0, earned, if (earned) 2 else 0, if (earned) "2026-10-01T00:00:00Z" else "")
    private val badges = listOf(badge(1, true), badge(2), badge(3, true, true), badge(4, image = true), badge(5), badge(6), badge(7))

    @Before fun images() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://badge.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch(): FetchResult {
                            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.RED)
                            val output = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle()
                            return SourceFetchResult(ImageSource(Buffer().write(output.toByteArray()), options.fileSystem), "image/png", DataSource.MEMORY)
                        }
                    }
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
    }
    @After fun restore() { SingletonImageLoader.setUnsafe(previous); loader.shutdown() }

    @Test fun phoneGridHasThreeColumnsAndLockedPictureLogosAreGray() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 700.dp))) {
                KudoTheme(DisplaySettings()) { BadgePage(badges) }
            }
        }
        val rows = (1..4).map { compose.onNodeWithTag("badge-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertEquals(rows[0].top, rows[1].top, 1f); assertEquals(rows[0].top, rows[2].top, 1f)
        assertTrue(rows[3].top > rows[0].bottom)
        compose.onNodeWithTag("badge-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已获得"))
        compose.onNodeWithTag("badge-2").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "未获得"))
        compose.onNodeWithText("收到一个赞").assertDoesNotExist()
        compose.waitUntil(5000) {
            val pixels = compose.onNodeWithTag("badge-logo-3", useUnmergedTree = true).captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2].red > .9f
        }
        compose.waitUntil(5000) {
            val pixels = compose.onNodeWithTag("badge-logo-4", useUnmergedTree = true).captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2].red in .15f.. .4f
        }
        val locked = compose.onNodeWithTag("badge-logo-4", useUnmergedTree = true).captureToImage().toPixelMap()
        val gray = locked[locked.width / 2, locked.height / 2]
        assertEquals(gray.red, gray.green, .02f); assertEquals(gray.green, gray.blue, .02f)
        assertTrue(gray.red > .1f)
        capture("badges-grid-light.png")
    }

    @Test fun badgeDetailsShowRealConditionsAwardDataAndLinksAndDragToClose() {
        var link = ""
        compose.setContent { KudoTheme(DisplaySettings()) { BadgePage(badges, website = { link = it }) } }
        compose.onNodeWithTag("badge-1").performClick()
        compose.onNodeWithText("获得时间：2026-10-01").assertIsDisplayed()
        compose.onNodeWithText("已获得 2 次").assertIsDisplayed()
        compose.onNodeWithText("收到社区成员的一个赞即可获得。").assertIsDisplayed()
        capture("badge-earned-details.png")
        // Inline links occupy their glyphs, not the full-width parent Text's click target.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("社区指南").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.onNodeWithText("社区指南").performTouchInput { click(layouts.single().getBoundingBox(1).center) }
        compose.runOnIdle { assertEquals("https://linux.do/guidelines", link) }
        compose.onNodeWithTag("sheet-drag-handle").performTouchInput { swipeDown(endY = height + 600f, durationMillis = 500) }
        compose.onNodeWithTag("liquid-action-sheet").assertDoesNotExist()
        compose.onNodeWithTag("badge-2").performClick()
        compose.onNodeWithText("未获得").assertIsDisplayed()
        compose.onNodeWithText("获得时间：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("在社区查看").performClick()
        compose.runOnIdle { assertEquals("https://linux.do/badges/2", link) }
    }

    @Test fun wideAndNarrowLargeTextAdaptTheirColumnCountsAndKeepDetailsScrollable() {
        var width by mutableStateOf(700.dp)
        var settings by mutableStateOf(DisplaySettings())
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width, 700.dp))) {
                KudoTheme(settings) { BadgePage(badges) }
            }
        }
        val first = compose.onNodeWithTag("badge-1").fetchSemanticsNode().boundsInRoot
        val sixth = compose.onNodeWithTag("badge-6").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, sixth.top, 1f)
        capture("badges-grid-wide.png")
        compose.runOnIdle { width = 280.dp; settings = DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f) }
        val narrow = (1..3).map { compose.onNodeWithTag("badge-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        assertEquals(narrow[0].top, narrow[1].top, 1f)
        assertTrue(narrow[2].top > narrow[0].bottom)
        capture("badges-grid-dark-narrow.png")
        compose.onNodeWithTag("badge-2").performClick()
        compose.onNodeWithText("在社区查看").performScrollTo().assertIsDisplayed()
        capture("badge-dark-narrow-details.png")
    }

    @Test fun packagedVectorsCoverTheSitesKnownIconsAndUnknownIconsHaveABadgeFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = listOf("angles-up", "baby", "bullhorn", "certificate", "pencil", "dragon", "square-check", "medal", "fab-git-alt",
            "hammer", "link", "stamp", "user-plus", "heart", "share-nodes", "user", "seedling", "reply", "cube", "far-eye",
            "face-smile", "file-signature", "envelope", "cake-candles", "water", "quote-right", "flag", "book-open-reader", "file-lines",
            "far-pen-to-square", "user-pen", "pen", "at")
        for (name in names) assertEquals(name, BadgeIcons.get(context, name).name)
        assertEquals("thumbs-up", BadgeIcons.get(context, "vote-up-filled").name)
        assertEquals("award", BadgeIcons.get(context, "a-future-unknown-badge").name)
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
