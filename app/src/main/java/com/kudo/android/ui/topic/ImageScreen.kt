package com.kudo.android.ui.topic

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun ImageScreen(gallery: ImageGalleryState, session: GallerySession, open: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = key(session.images) { rememberPagerState(initialPage = session.index) { session.images.size } }
    val transforms = remember(session.images) { session.images.map { GalleryTransformState(it.ratio) } }
    val transform = transforms[pager.currentPage]
    val window = rememberGalleryWindow()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var information by remember { mutableStateOf(false) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(session::select) }
    LaunchedEffect(Unit) { focusManager.clearFocus(force = true); keyboard?.hide(); focus.requestFocus() }
    fun close() {
        if (!session.closing) scope.launch {
            gallery.dismiss(pager.currentPage, prepare = { window?.restoreAndAwaitLayout() })
        }
    }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = transform.scrimAlpha)).testTag("image-viewer")
        .semantics { paneTitle = "图片查看器" }
        .then(if (information) Modifier.clearAndSetSemantics {} else Modifier)
        .focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyUp) false else when (event.key) {
                Key.Escape -> { close(); true }
                Key.DirectionLeft -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                Key.DirectionRight -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(session.images.lastIndex)) }; true }
                else -> false
            }
        }.focusable()) {
        HorizontalPager(pager, Modifier.fillMaxSize().testTag("image-pager"),
            userScrollEnabled = !session.closing && !transform.zoomed && !transform.dragging,
            key = { "${it}:${session.images[it].originalUrl}" }) { page ->
            GalleryImagePage(session.images[page], page, transforms[page], !session.closing && !information,
                active = page == pager.currentPage, close = ::close, information = { information = true },
                placeholder = gallery.openingAnchor?.painter?.takeIf { page == session.index })
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (session.loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            if (session.error) TextButton(onClick = session::retryAlbum) { Text("其他图片加载失败，点击重试", color = Color.White) }
            if (session.images.size > 1) Text("${pager.currentPage + 1} / ${session.images.size}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (information) {
        val image = session.images[pager.currentPage]
        AlertDialog(onDismissRequest = { information = false },
            title = { Text("图片信息") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (image.filename.isNotBlank()) Text(image.filename)
                if (image.dimensions.isNotBlank()) Text(image.dimensions)
                if (image.sizeLabel.isNotBlank()) Text(image.sizeLabel)
                if (image.filename.isBlank() && image.dimensions.isBlank() && image.sizeLabel.isBlank()) Text("此图片没有附带文件信息")
            } },
            confirmButton = { TextButton(onClick = { information = false; open(image.originalUrl) }) { Text("浏览器打开") } },
            dismissButton = { TextButton(onClick = { information = false }) { Text("关闭") } })
    }
}

private tailrec fun Context.galleryActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.galleryActivity()
    else -> null
}

private class GalleryWindow(val view: View, val controller: WindowInsetsControllerCompat) {
    private val insets = ViewCompat.getRootWindowInsets(view)
    private val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) != false
    private val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) != false
    private val behavior = controller.systemBarsBehavior
    fun hide() {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    fun restore() {
        controller.systemBarsBehavior = behavior
        if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars())
        if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
    }
    suspend fun restoreAndAwaitLayout() {
        restore()
        withTimeoutOrNull(400) {
            do {
                withFrameNanos { }
                val current = ViewCompat.getRootWindowInsets(view)
                val ready = (!statusVisible || current?.isVisible(WindowInsetsCompat.Type.statusBars()) == true) &&
                    (!navigationVisible || current?.isVisible(WindowInsetsCompat.Type.navigationBars()) == true)
            } while (!ready)
            withFrameNanos { }
        }
    }
}

@Composable
private fun rememberGalleryWindow(): GalleryWindow? {
    val activity = LocalContext.current.galleryActivity()
    val view = LocalView.current
    val window = remember(activity, view) { activity?.let { GalleryWindow(view, WindowCompat.getInsetsController(it.window, view)) } }
    DisposableEffect(window) { window?.hide(); onDispose { window?.restore() } }
    return window
}
