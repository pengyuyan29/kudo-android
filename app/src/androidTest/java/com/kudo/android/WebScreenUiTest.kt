package com.kudo.android

import android.content.Context
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.kudo.android.data.AndroidSessionCookies
import com.kudo.android.data.DisplaySettings
import com.kudo.android.data.ThemeMode
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.web.WebScreen
import org.junit.*
import org.junit.Assert.*
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WebScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val views = mutableListOf<FixtureWebView>()
    private val loadedTitle = AtomicReference("")
    private val loads = AtomicInteger()
    private var failFirst = false
    @Volatile private var responseGate: CountDownLatch? = null
    private val start = "https://reader-fixture.test/guidelines"

    private fun create(context: Context) = FixtureWebView(context).also { views += it }
    private fun current() = views.last()
    private fun show(close: () -> Unit = {}, external: (String) -> Unit = {}) {
        compose.setContent { KudoTheme(DisplaySettings()) { WebScreen(start, close, external, ::create) } }
        awaitPage("指南")
    }
    private fun awaitPage(title: String) {
        compose.waitUntil(10000) { loadedTitle.get() == title }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("web-loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
    private fun javascript(script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        compose.runOnUiThread { current().evaluateJavascript(script) { result = it; latch.countDown() } }
        assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun click(id: String) {
        val point = JSONArray(javascript("(()=>{const r=document.getElementById('$id').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth]})()"))
        val node = compose.onNodeWithTag("web-content")
        val scale = node.fetchSemanticsNode().boundsInRoot.width / point.getDouble(2).toFloat()
        loadedTitle.set("")
        node.performTouchInput { click(Offset(point.getDouble(0).toFloat() * scale, point.getDouble(1).toFloat() * scale)) }
    }

    @After fun removeFixtureCookies() {
        responseGate?.countDown()
        compose.runOnUiThread {
            val manager = CookieManager.getInstance()
            listOf("app_fixture", "web_fixture").forEach {
                manager.setCookie(start, "$it=; Max-Age=0; Path=/; Secure")
            }
            manager.flush()
        }
    }

    @Test fun initialPageShowsLoadingUntilContentAppearsEvenIfProgressFinishesFirst() {
        val gate = CountDownLatch(1)
        responseGate = gate
        try {
            compose.setContent { KudoTheme(DisplaySettings()) { WebScreen(start, {}, {}, ::create) } }
            compose.onNodeWithTag("web-loading").assertIsDisplayed()
            compose.onNodeWithText("reader-fixture.test").assertDoesNotExist()
            compose.runOnUiThread { current().webChromeClient?.onProgressChanged(current(), 100) }
            compose.onNodeWithTag("web-loading").assertIsDisplayed()
            gate.countDown()
            awaitPage("指南")
            compose.onNodeWithTag("web-loading").assertDoesNotExist()
            compose.onNodeWithText("reader-fixture.test").assertDoesNotExist()
        } finally { gate.countDown() }
    }

    @Test fun refreshingShowsLoadingAgainUntilContentAppears() {
        show()
        val gate = CountDownLatch(1)
        responseGate = gate
        try {
            loadedTitle.set("")
            compose.onNodeWithContentDescription("刷新网页").performClick()
            compose.onNodeWithTag("web-loading").assertIsDisplayed()
            gate.countDown()
            awaitPage("指南")
            compose.onNodeWithTag("web-loading").assertDoesNotExist()
        } finally { gate.countDown() }
    }

    @Test fun siteLinksNewWindowsAndRedirectsStayInWebHistory() {
        var closed = 0
        val external = mutableListOf<String>()
        show({ closed++ }, { external += it })
        click("topic"); awaitPage("网页帖子")
        click("profile"); awaitPage("网页用户")
        click("category"); awaitPage("网页分类")
        click("blank"); awaitPage("新窗口网页")
        click("redirect"); awaitPage("重定向后的网页帖子")
        compose.runOnIdle { assertEquals(1, views.size); assertEquals(0, closed); assertTrue(external.isEmpty()) }
        compose.onNodeWithContentDescription("返回").performClick()
        awaitPage("新窗口网页")
        compose.onNodeWithContentDescription("在浏览器打开").performClick()
        compose.runOnIdle { assertEquals(listOf("https://linux.do/t/topic/3001000/9"), external) }
        compose.onNodeWithContentDescription("关闭网页").performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun cookieStoreIsSharedWithAppAndIsolatedFromOtherOrigins() {
        compose.runOnUiThread { AndroidSessionCookies().set(start, "app_fixture=from_app; Path=/; Secure; SameSite=Lax") }
        show()
        assertTrue(javascript("document.cookie").contains("app_fixture=from_app"))
        javascript("document.cookie='web_fixture=from_web; Path=/; Secure; SameSite=Lax'")
        compose.runOnIdle {
            assertTrue(AndroidSessionCookies().get(start).contains("web_fixture=from_web"))
            assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(current()))
            assertFalse(current().settings.allowFileAccess)
            assertFalse(current().settings.allowContentAccess)
        }
        click("other"); awaitPage("其他网站")
        assertFalse(javascript("document.cookie").contains("app_fixture"))
        assertFalse(javascript("document.cookie").contains("web_fixture"))
    }

    @Test fun restoredPageKeepsItsHistoryAndClosingReleasesTheWebView() {
        val restoration = StateRestorationTester(compose)
        var visible by mutableStateOf(true)
        restoration.setContent { KudoTheme(DisplaySettings()) {
            if (visible) WebScreen(start, { visible = false }, {}, ::create) else Text("原话题")
        } }
        awaitPage("指南")
        click("topic"); awaitPage("网页帖子")
        loadedTitle.set("")
        restoration.emulateSavedInstanceStateRestore()
        awaitPage("网页帖子")
        compose.runOnIdle { assertEquals(2, views.size); assertTrue(views.first().released); assertTrue(current().canGoBack()) }
        compose.onNodeWithContentDescription("返回").performClick()
        awaitPage("指南")
        compose.onNodeWithContentDescription("关闭网页").performClick()
        compose.onNodeWithText("原话题").assertIsDisplayed()
        compose.runOnIdle { assertTrue(current().released) }
    }

    @Test fun errorsCanRetryAndRecompositionDoesNotReloadTheInitialPage() {
        failFirst = true
        var dark by mutableStateOf(false)
        compose.setContent { KudoTheme(DisplaySettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
            WebScreen(start, {}, {}, ::create)
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("web-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("web-loading").assertDoesNotExist()
        compose.onNodeWithText("重新加载").performClick()
        awaitPage("指南")
        val previous = loads.get()
        compose.runOnIdle { dark = true }
        compose.runOnIdle { assertEquals(previous, loads.get()); assertEquals(1, views.size) }
        compose.onNodeWithContentDescription("刷新网页").performClick()
        compose.waitUntil(10000) { loads.get() > previous }
        awaitPage("指南")
    }

    @Test fun cssViewportHeightDoesNotCollapseEmbeddedFrames() {
        show()
        val dimensions = JSONArray(javascript("[innerHeight, document.getElementById('vh').offsetHeight, document.getElementById('frame').offsetHeight]"))
        assertEquals("100vh must match the viewport", dimensions.getDouble(0), dimensions.getDouble(1), 2.0)
        assertEquals("Viewport cap must not hide the frame", 76.0, dimensions.getDouble(2), 1.0)
    }

    private inner class FixtureWebView(context: Context) : WebView(context) {
        var released = false
        private val pendingError = AtomicReference<Pair<WebResourceRequest, WebResourceResponse>?>(null)
        override fun destroy() { released = true; super.destroy() }
        override fun setWebViewClient(client: WebViewClient) {
            super.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = client.shouldOverrideUrlLoading(view, request)
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    if (request.isForMainFrame) responseGate?.await(10, TimeUnit.SECONDS)
                    val value = request.url.toString()
                    val attempt = loads.incrementAndGet()
                    val title = when {
                        value.contains("/redirect") -> "跳转中"
                        value.contains("/9") -> "新窗口网页"
                        value.contains("/10") -> "重定向后的网页帖子"
                        value.contains("/t/") -> "网页帖子"
                        value.contains("/u/") -> "网页用户"
                        value.contains("/c/") -> "网页分类"
                        value.contains("other-fixture") -> "其他网站"
                        else -> "指南"
                    }
                    val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>$title</title>
                        <style>#vh {position:absolute;visibility:hidden;height:100vh} #frame {border:0;height:76px;max-height:min(1000px,200vh)}</style></head><body>
                        <div id="vh"></div><iframe id="frame" srcdoc="fixture"></iframe>
                        <h1>$title</h1>
                        <a id="topic" href="https://linux.do/t/topic/3001000/8">帖子</a>
                        <a id="profile" href="https://linux.do/u/stingysheep/activity/">用户</a>
                        <a id="category" href="https://linux.do/c/develop/4">分类</a>
                        <a id="blank" target="_blank" href="https://linux.do/t/topic/3001000/9">新窗口</a>
                        <a id="redirect" href="https://reader-fixture.test/redirect">跳转</a>
                        <a id="other" href="https://other-fixture.test/page">外站</a>
                        ${if (value.contains("/redirect")) "<script>location.replace('https://linux.do/t/topic/3001000/10')</script>" else ""}
                        </body></html>"""
                    val response = WebResourceResponse("text/html", "UTF-8", if (failFirst && attempt == 1) 500 else 200,
                        if (failFirst && attempt == 1) "Server Error" else "OK", emptyMap(), html.byteInputStream())
                    // Intercepted responses skip the network stack's HTTP-error callback on this WebView.
                    if (response.statusCode >= 400) pendingError.set(request to response)
                    return response
                }
                override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) = client.onPageStarted(view, url, icon)
                override fun onPageCommitVisible(view: WebView, url: String) = client.onPageCommitVisible(view, url)
                override fun doUpdateVisitedHistory(view: WebView, url: String, reload: Boolean) = client.doUpdateVisitedHistory(view, url, reload)
                override fun onPageFinished(view: WebView, url: String) {
                    client.onPageFinished(view, url)
                    pendingError.getAndSet(null)?.let { (request, response) -> client.onReceivedHttpError(view, request, response) }
                    loadedTitle.set(view.title)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) = client.onReceivedError(view, request, error)
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) = client.onReceivedHttpError(view, request, response)
            })
        }
    }
}
