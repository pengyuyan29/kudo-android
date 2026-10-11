package com.kudo.android.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kudo.android.ui.LinuxDoLink
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.LogoYellow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private class WebPageState(val saved: Bundle? = null) {
    var view: WebView? = null
    fun save() = Bundle().also { bundle ->
        if (view?.saveState(bundle) == null) saved?.let(bundle::putAll)
    }
}

/** Native routing ends at this screen: links and redirects stay in the same web history. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebScreen(url: String, back: () -> Unit, openBrowser: (String) -> Unit,
    webViewFactory: (Context) -> WebView = { WebView(it) }) {
    val page = rememberSaveable(url, saver = Saver<WebPageState, Bundle>(save = { it.save() }, restore = { WebPageState(it) })) { WebPageState() }
    var currentUrl by rememberSaveable(url) { mutableStateOf(url) }
    var title by rememberSaveable(url) { mutableStateOf("网页") }
    var progress by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var message by remember(url) { mutableStateOf<String?>(null) }
    var scrolled by remember(url) { mutableStateOf(false) }
    val currentBack by rememberUpdatedState(back)
    fun navigateBack() { val view = page.view; if (view?.canGoBack() == true) view.goBack() else currentBack() }
    fun retry() { error = null; message = null; page.view?.reload() }
    BackHandler { navigateBack() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, page) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> page.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> { page.view?.onPause(); CookieManager.getInstance().flush() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("web-page")) {
        PageTopBar(title, ::navigateBack) {
            CommunityIconButton(Icons.Rounded.Refresh, "刷新网页", ::retry)
            CommunityIconButton(Icons.Rounded.OpenInBrowser, "在浏览器打开", { openBrowser(currentUrl) })
            CommunityIconButton(Icons.Rounded.Close, "关闭网页", { currentBack() })
        }
        Text(currentUrl.toHttpUrlOrNull()?.host.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress < 100) Box(Modifier.fillMaxWidth((progress / 100f).coerceIn(0f, 1f)).fillMaxHeight().background(LogoYellow))
        }
        message?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()
            .quickScrollTarget(scrolled = scrolled, enabled = error == null) {
                page.view?.let { view ->
                    androidx.compose.animation.core.animate(view.scrollY.toFloat(), 0f) { value, _ -> view.scrollTo(0, value.toInt()) }
                }
            }) {
            AndroidView(factory = { context ->
                webViewFactory(context).apply {
                    // Keep CSS viewport units bounded; AndroidView otherwise defaults to WRAP_CONTENT.
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            // Never dispatch to native routes, including Linux.do topics, profiles and categories.
                            val blocked = LinuxDoLink.webUrl(request.url.toString()) == null
                            if (blocked && request.isForMainFrame) message = "暂不支持此类链接"
                            return blocked
                        }
                        override fun onPageStarted(view: WebView, value: String, favicon: Bitmap?) {
                            currentUrl = value; progress = 0; error = null; message = null
                        }
                        override fun doUpdateVisitedHistory(view: WebView, value: String, isReload: Boolean) { currentUrl = value }
                        override fun onPageFinished(view: WebView, value: String) { progress = 100; CookieManager.getInstance().flush() }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                            if (request.isForMainFrame) { progress = 100; error = "网页加载失败，请检查网络后重试。" }
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame && response.statusCode >= 400) { progress = 100; error = "网页返回 ${response.statusCode}，请稍后重试。" }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                        override fun onReceivedTitle(view: WebView, value: String?) { title = value?.takeIf(String::isNotBlank) ?: "网页" }
                    }
                    setDownloadListener { _, _, _, _, _ -> message = "下载文件请使用右上角的浏览器打开" }
                    page.view = this
                    setOnScrollChangeListener { _, _, y, _, _ -> scrolled = y > 0 }
                    if (page.saved?.let { restoreState(it) } == null) loadUrl(url)
                }
            }, onRelease = { view ->
                page.view = null
                view.setOnScrollChangeListener(null)
                view.stopLoading(); view.removeAllViews(); view.destroy()
                CookieManager.getInstance().flush()
            }, modifier = Modifier.fillMaxSize().testTag("web-content"))
            error?.let { text ->
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp).testTag("web-error"),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                    CommunityButton("重新加载", ::retry)
                }
            }
        }
    }
}
