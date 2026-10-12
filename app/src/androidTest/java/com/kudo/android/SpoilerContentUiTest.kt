package com.kudo.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.*
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(coil3.annotation.DelicateCoilApi::class)
class SpoilerContentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    @Before fun images() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 160) bitmap.setPixel(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xff46c598.toInt() else 0xff202020.toInt())
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        loader = ImageLoader.Builder(context).components { add(object : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                if (!data.toString().startsWith("https://spoiler.test/")) return null
                return object : Fetcher { override suspend fun fetch() = SourceFetchResult(
                    ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY) }
            }
        }) }.build()
        SingletonImageLoader.setUnsafe(loader)
    }
    @After fun cleanup() { SingletonImageLoader.setUnsafe(previous); loader.shutdown() }
    private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    @Test fun blockTextEmojiAndImageToggleWithoutOpeningTheImage() {
        var opened = 0
        compose.setContent { KudoTheme(DisplaySettings()) { Column {
            RichContent("""<div class="spoiler"><p>秘密文本<img class="emoji" src="https://spoiler.test/emoji.png" alt=":ok:"></p><img src="https://spoiler.test/photo.png" width="160" height="100"></div>""", { opened++ }, { opened++ })
        } } }
        val region = compose.onNodeWithTag("article-spoiler-root-0")
        region.assert(state("剧透已隐藏"))
        compose.onNodeWithText("秘密文本", substring = true).assertDoesNotExist()
        region.performTouchInput { click() }
        region.assert(state("剧透已显示"))
        compose.onNodeWithText("秘密文本", substring = true).assertIsDisplayed()
        region.performTouchInput { click() }
        region.assert(state("剧透已隐藏"))
        compose.runOnIdle { assertEquals(0, opened) }
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "spoiler-hidden.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
    @Test fun inlineSpoilerKeepsSurroundingTextAndRestoresItsStateWithoutFollowingItsLink() {
        val restoration = StateRestorationTester(compose)
        var position = Offset.Zero
        var links = 0
        restoration.setContent { KudoTheme(DisplaySettings()) {
            val measured = rememberTextMeasurer().measure("前秘密后", MaterialTheme.typography.bodyLarge)
            position = measured.getBoundingBox(1).center
            RichContent("""<p>前<span class="spoiler"><a href="https://example.com/">秘密</a></span>后</p>""", {}, { links++ })
        } }
        val text = compose.onNodeWithTag("article-text-root-0")
        text.assertTextEquals("前剧透内容后").assert(state("剧透已隐藏"))
        text.performTouchInput { click(position) }
        text.assertTextEquals("前秘密后").assert(state("剧透已显示"))
        restoration.emulateSavedInstanceStateRestore()
        text.assert(state("剧透已显示"))
        text.performTouchInput { click(position) }
        text.assertTextEquals("前剧透内容后")
        compose.runOnIdle { assertEquals(0, links) }
    }
    @Test fun oldAndroidFallbackCoversTheContentInsteadOfShowingItUnblurred() {
        compose.setContent { KudoTheme(DisplaySettings()) {
            SpoilerRegion("fallback", "fallback-spoiler", blurSupported = false) {
                Text("秘密", Modifier.fillMaxWidth().background(Color.Red))
            }
        } }
        val region = compose.onNodeWithTag("fallback-spoiler")
        val hidden = region.captureToImage().toPixelMap()
        assertTrue(hidden[2, 2].green > 0.1f)
        region.performTouchInput { click() }
        val shown = region.captureToImage().toPixelMap()
        assertTrue(shown[2, 2].red > 0.8f && shown[2, 2].green < 0.1f)
    }

    @Test fun obsidianCalloutRendersTitleAndCanBeExpanded() {
        compose.setContent { KudoTheme(DisplaySettings()) {
            RichContent("""<blockquote><p>[!note]-</p><p>隐藏的提示正文</p></blockquote>""", {}, {})
        } }
        compose.onNodeWithTag("article-callout-root-0").assertIsDisplayed()
        compose.onNodeWithText("Note").assertIsDisplayed()
        compose.onNodeWithText("隐藏的提示正文").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开提示").performClick()
        compose.onNodeWithText("隐藏的提示正文").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起提示").assertIsDisplayed()
    }
}
