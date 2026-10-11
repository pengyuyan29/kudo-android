package com.kudo.android

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.*
import coil3.decode.*
import coil3.fetch.*
import coil3.request.*
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.RichContent
import kotlinx.coroutines.CompletableDeferred
import okio.Buffer
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(coil3.annotation.DelicateCoilApi::class)
class ArticleImageLoadingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var previous: ImageLoader
    private lateinit var loader: ImageLoader
    private val gate = CompletableDeferred<Unit>()
    private val requests = AtomicInteger()
    private var failFirst = false

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous = SingletonImageLoader.get(context)
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff46c598.toInt()) }
        val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        loader = ImageLoader.Builder(context).memoryCache(null).diskCache(null).components {
            add(object : Fetcher.Factory<Uri> {
                override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                    if (data.toString() != "https://image-loading.test/picture.png") return null
                    return object : Fetcher {
                        override suspend fun fetch(): SourceFetchResult {
                            val attempt = requests.incrementAndGet()
                            gate.await()
                            if (failFirst && attempt == 1) throw IOException("fixture offline")
                            return SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), "image/png", DataSource.MEMORY)
                        }
                    }
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
    }
    @After fun cleanup() { gate.complete(Unit); SingletonImageLoader.setUnsafe(previous); loader.shutdown() }
    private fun show() = compose.setContent { KudoTheme(DisplaySettings()) { Column {
        RichContent("""<img src="https://image-loading.test/picture.png" width="800" height="600">""", {}, {})
    } } }
    @Test fun loadingStaysVisibleUntilTheImageIsDecoded() {
        show()
        compose.waitUntil(5000) { requests.get() == 1 }
        compose.onNodeWithTag("article-image-article-0-loading").assertIsDisplayed()
        gate.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("article-image-article-0-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("加载失败，点击重试").assertDoesNotExist()
    }
    @Test fun failureStopsLoadingAndRetryActuallyFetchesAgain() {
        failFirst = true
        gate.complete(Unit)
        show()
        compose.onNodeWithText("加载失败，点击重试").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { requests.get() == 2 && compose.onAllNodesWithText("加载失败，点击重试").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("article-image-article-0-loading").assertDoesNotExist()
    }
}
