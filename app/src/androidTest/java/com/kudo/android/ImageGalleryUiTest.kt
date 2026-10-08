package com.kudo.android

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.kudo.android.data.*
import com.kudo.android.ui.components.TopicCard
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
@OptIn(coil3.annotation.DelicateCoilApi::class)
class ImageGalleryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var observedGallery: ImageGalleryState? = null
    private val author = Member(21, "gallery", "图片测试作者", "", avatarDetailsLoaded = true)
    private val loaded = CopyOnWriteArrayList<String>()
    private lateinit var previousLoader: ImageLoader
    private lateinit var loader: ImageLoader
    private val palette = listOf(0xFFFFCF52.toInt(), 0xFF46C598.toInt(), 0xFF508CF0.toInt(), 0xFFEB819B.toInt(), 0xFF9F81EA.toInt())
    private val pictures = (1..5).map { number -> ArticleBlock.Image("https://gallery.test/$number.png", "图片 $number", 4f / 3f,
        filename = "figure-$number.png", dimensions = "800×600", sizeLabel = "24 KB") }

    @Before fun images() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previousLoader = SingletonImageLoader.get(context)
        val bytes = (1..5).associate { "$it.png" to png(800, 600, palette[it - 1], it.toString()) } +
            mapOf("wide.png" to png(1688, 339, palette[0], "1688 x 339"))
        loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (!data.toString().startsWith("https://gallery.test/")) return null
                    return object : Fetcher {
                        override suspend fun fetch(): SourceFetchResult = SourceFetchResult(
                            ImageSource(Buffer().write(bytes.getValue(data.toString().substringAfterLast('/'))), options.fileSystem),
                            "image/png", DataSource.MEMORY)
                    }
                }
            })
        }.eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { loaded += request.data.toString() }
        }).build()
        SingletonImageLoader.setUnsafe(loader)
    }

    @After fun restore() { SingletonImageLoader.setUnsafe(previousLoader); loader.shutdown() }

    private fun html(images: List<ArticleBlock.Image>) = images.joinToString("") { image ->
        """<div class="lightbox-wrapper"><a class="lightbox" href="${image.originalUrl}"><img src="${image.url}" width="${if (image.url.endsWith("wide.png")) 1688 else 800}" height="${if (image.url.endsWith("wide.png")) 339 else 600}" alt="${image.alt}"><div class="meta"><span class="filename">${image.filename}</span><span class="informations">${image.dimensions} ${image.sizeLabel}</span></div></a></div>"""
    }

    @Composable private fun Detail(main: List<ArticleBlock.Image> = pictures, comments: Boolean = false, openLink: (String) -> Unit = {}) {
        val first = Post(10, 1, author, "<p>查看图片及返回位置</p>" + html(main), "2026-10-09T00:00:00Z", 0, false, null, null, true)
        val replies = if (comments) listOf(
            first.copy(id = 11, number = 2, cooked = html(pictures.take(2))),
            first.copy(id = 12, number = 3, cooked = html(pictures.takeLast(3))),
        ) else emptyList()
        val posts = listOf(first) + replies
        KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
            ImageGalleryHost({ pictures }, openLink) {
                val gallery = LocalImageGallery.current
                SideEffect { observedGallery = gallery }
                TopicScreen(TopicState(TopicDetail(5, "图片查看器验收", 1, posts, posts.map { it.id }, false), false),
                    GlassMode.LIQUID, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }

    private fun openFirst() {
        compose.waitUntil(5_000) { loaded.contains(pictures.first().url) }
        compose.onNodeWithTag("article-image-post-10-0").performTouchInput { click() }
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
    }

    private fun third() {
        repeat(2) { compose.onNodeWithTag("image-pager").performTouchInput { swipeLeft(durationMillis = 500) } }
        compose.onNodeWithText("3 / 5").assertIsDisplayed()
    }

    @Test fun wideImageUsesItsRealHeightAndAutomaticInformationIsHidden() {
        val wide = pictures.first().copy(url = "https://gallery.test/wide.png", originalUrl = "https://gallery.test/wide.png",
            ratio = 1688f / 339f, filename = "20261008230932_14_506", dimensions = "1688×339", sizeLabel = "22.8 KB")
        compose.setContent { Detail(listOf(wide)) }
        compose.waitUntil(5_000) { loaded.contains(wide.url) }
        val bounds = compose.onNodeWithTag("article-image-post-10-0").fetchSemanticsNode().boundsInRoot
        assertEquals(1688f / 339f, bounds.width / bounds.height, 0.03f)
        compose.onNodeWithText(wide.filename).assertDoesNotExist()
        compose.onNodeWithText("1688×339 22.8 KB").assertDoesNotExist()
        capture("gallery-wide-inline.png")
    }

    @Test fun postPagerClosesToThirdImageAboveTheBottomActions() {
        compose.setContent { Detail() }
        openFirst()
        third()
        capture("gallery-third-fullscreen.png")
        compose.onNodeWithTag("gallery-page-2").performTouchInput { click() }
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
        val image = compose.onNodeWithTag("article-image-post-10-2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("topic-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("返回图不能被底部操作栏遮住：$image / $actions", image.bottom <= actions.top + 2)
        capture("gallery-return-third.png")
    }

    @Test fun commentsKeepSeparateAlbums() {
        compose.setContent { Detail(emptyList(), comments = true) }
        compose.onNodeWithTag("article-image-post-11-0").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithTag("image-pager").performTouchInput { swipeLeft(durationMillis = 500) }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithTag("gallery-page-1").performTouchInput { click() }
        compose.onNodeWithTag("article-image-post-11-1").assertIsDisplayed()
    }

    @Test fun listPreviewDoesNotOpenTheTopicAndReturnsTheCurrentPicture() {
        var topicOpens = 0
        val topic = Topic(9, "列表多图测试", "点击预览查看相册", author, 1, emptyList(), 0, 0, 0, pictures.first().url, "2026-10-09T00:00:00Z")
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.LIGHT)) {
            ImageGalleryHost({ pictures }, {}) {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp)) { TopicCard(topic, null, { topicOpens++ }, {}) }
            }
        } }
        compose.waitUntil(5_000) { loaded.contains(pictures.first().url) }
        compose.onNodeWithTag("topic-image-9").performTouchInput { click() }
        third()
        compose.onNodeWithTag("gallery-page-2").performTouchInput { click() }
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, topicOpens) }
        val preview = compose.onNodeWithTag("topic-image-9").captureToImage().toPixelMap()
        val color = preview[preview.width / 4, preview.height / 4]
        assertTrue("第3张的蓝色预览必须留在列表", color.blue > color.red && color.blue > color.green)
        capture("gallery-list-third.png")
    }

    @Test fun pinchAndPanningDoNotTurnPagesOrCloseTheViewer() {
        compose.setContent { Detail() }
        openFirst()
        val page = compose.onNodeWithTag("gallery-page-0")
        page.performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.5f))
            down(1, Offset(width * 0.6f, height * 0.5f))
            moveTo(0, Offset(width * 0.2f, height * 0.5f), delayMillis = 200)
            moveTo(1, Offset(width * 0.8f, height * 0.5f), delayMillis = 200)
            up(0); up(1)
        }
        page.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已放大 3.0 倍"))
        page.performTouchInput { swipeDown(durationMillis = 500) }
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
        compose.onNodeWithText("1 / 5").assertIsDisplayed()
        val reset = page.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "还原图片" }
        compose.runOnIdle { assertTrue(reset.action()) }
        page.performTouchInput { click() }
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
    }

    @Test fun shortPullAndCancelledPullRestoreTheImageWhileLongPullCloses() {
        compose.setContent { Detail() }
        openFirst()
        val photo = compose.onNodeWithTag("gallery-photo-0", useUnmergedTree = true)
        val resting = photo.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("gallery-page-0").performTouchInput {
            swipe(center, center + Offset(0f, height * 0.08f), durationMillis = 600)
        }
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
        compose.runOnIdle { assertFalse("小幅下拉不能开始关闭", observedGallery?.session?.closing != false) }
        assertEquals(resting.top, photo.fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("gallery-page-0").performTouchInput {
            down(center)
            repeat(12) { moveBy(Offset(0f, height * 0.01f), delayMillis = 30) }
            cancel()
        }
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
        assertEquals(resting.top, photo.fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("gallery-page-0").performTouchInput {
            swipe(Offset(width / 2f, height * 0.3f), Offset(width / 2f, height * 0.8f), durationMillis = 650)
        }
        compose.onNodeWithTag("image-viewer").assertDoesNotExist()
    }

    @Test fun longPressShowsOnlyCurrentPictureInformationAndKeepsTheViewerOpen() {
        var opened = ""
        compose.setContent { Detail(openLink = { opened = it }) }
        openFirst()
        third()
        compose.onNodeWithTag("gallery-page-2").performTouchInput { longClick() }
        compose.onNodeWithText("图片信息").assertIsDisplayed()
        compose.onNodeWithText("figure-3.png").assertIsDisplayed()
        compose.onNodeWithText("浏览器打开").performTouchInput { click() }
        compose.runOnIdle { assertEquals(pictures[2].originalUrl, opened) }
        compose.onNodeWithTag("image-viewer").assertIsDisplayed()
    }

    @Test fun restoringStateKeepsTheThirdPageAndSystemBackReturnsToItsSource() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Detail() }
        openFirst()
        third()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("3 / 5").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("image-viewer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("article-image-post-10-2").assertIsDisplayed()
    }

    private fun png(width: Int, height: Int, color: Int, label: String): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(color)
            drawText(label, width * 0.4f, height * 0.58f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.BLACK; textSize = height * 0.18f })
        }
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(250)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
