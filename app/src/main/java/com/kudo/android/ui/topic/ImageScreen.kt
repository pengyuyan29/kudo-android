package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun ImageScreen(gallery: ImageGalleryState, session: GallerySession, open: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = key(session.images) { rememberPagerState(initialPage = session.index) { session.images.size } }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(session::select) }
    fun close() { scope.launch { gallery.dismiss(pager.currentPage) } }
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Color(0xFF101010)).testTag("image-viewer")
        .semantics { paneTitle = "图片查看器" }) {
        HorizontalPager(pager, Modifier.fillMaxSize().testTag("image-pager"),
            userScrollEnabled = !session.closing, key = { "${it}:${session.images[it].originalUrl}" }) { page ->
            AsyncImage(session.images[page].originalUrl, "第 ${page + 1} 张图片",
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = ::close) { Icon(Icons.Rounded.Close, "关闭图片", tint = Color.White) }
            IconButton(onClick = { open(session.current.originalUrl) }) { Icon(Icons.Rounded.OpenInBrowser, "在浏览器打开或保存", tint = Color.White) }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (session.loading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            if (session.error) TextButton(onClick = session::retryAlbum) { Text("其他图片加载失败，点击重试", color = Color.White) }
            Text("${pager.currentPage + 1} / ${session.images.size}", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
