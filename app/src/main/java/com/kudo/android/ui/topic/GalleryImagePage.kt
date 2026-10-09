package com.kudo.android.ui.topic

import android.graphics.drawable.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun GalleryImagePage(
    image: ArticleBlock.Image, page: Int, transform: GalleryTransformState,
    enabled: Boolean, active: Boolean, close: () -> Unit, information: () -> Unit,
    placeholder: Painter? = null, onPainter: (Painter) -> Unit = {}, onPainterDisposed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var animation by remember(image.originalUrl) { mutableStateOf<Animatable?>(null) }
    var failed by remember(image.originalUrl) { mutableStateOf(false) }
    var loaded by remember(image.originalUrl) { mutableStateOf(false) }
    var retry by remember(image.originalUrl) { mutableIntStateOf(0) }
    var settling by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(image.originalUrl) { onDispose { animation?.stop(); onPainterDisposed() } }
    val currentClose by rememberUpdatedState(close)
    val currentInformation by rememberUpdatedState(information)
    val currentEnabled by rememberUpdatedState(enabled)
    val flingVelocity = with(LocalDensity.current) { 1_000.dp.toPx() }
    val viewport = transform.viewport
    val request = remember(image.originalUrl, viewport, retry) {
        ImageRequest.Builder(context).data(image.originalUrl).apply {
            if (!viewport.isEmpty()) {
                val sampling = min(2f, min(4096f / maxOf(viewport.width, viewport.height),
                    sqrt(8_000_000f / (viewport.width * viewport.height))))
                size((viewport.width * sampling).roundToInt().coerceAtLeast(1), (viewport.height * sampling).roundToInt().coerceAtLeast(1))
            }
        }.build()
    }
    LifecycleResumeEffect(animation, active) {
        val drawable = animation
        if (active) { if (drawable?.isRunning == false) drawable.start() } else drawable?.stop()
        onPauseOrDispose { drawable?.stop() }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { transform.resize(Size(it.width.toFloat(), it.height.toFloat())) }
        .testTag("gallery-page-$page")
        .pointerInput(transform, enabled) {
            if (enabled) detectTapGestures(onTap = { currentClose() }, onLongPress = { currentInformation() })
        }
        .galleryGestures(transform, enabled, flingVelocity,
            start = { settling?.cancel(); transform.dismissOffset = androidx.compose.ui.geometry.Offset.Zero },
            finish = { dismiss ->
                if (currentEnabled) {
                    if (dismiss) currentClose()
                    else if (galleryMotionEnabled(context)) settling = scope.launch { transform.restoreDismiss() }
                    else transform.dismissOffset = androidx.compose.ui.geometry.Offset.Zero
                }
            })
        .semantics {
            contentDescription = "第 ${page + 1} 张图片"
            stateDescription = if (transform.zoomed) "已放大 ${"%.1f".format(transform.scale)} 倍" else "可双指缩放，下拉或点按关闭"
            onClick("关闭图片") { if (enabled) currentClose(); enabled }
            onLongClick("图片操作") { if (enabled) currentInformation(); enabled }
            customActions = listOf(
                CustomAccessibilityAction("放大图片") { if (enabled) transform.accessibleZoom(); enabled },
                CustomAccessibilityAction("还原图片") { if (enabled) transform.resetZoom(); enabled },
            )
        }) {
        key(retry) { AsyncImage(request, null, Modifier.fillMaxSize().graphicsLayer {
            scaleX = transform.scale * transform.dismissScale
            scaleY = scaleX
            translationX = transform.offset.x + transform.dismissOffset.x
            translationY = transform.offset.y + transform.dismissOffset.y
        }.testTag("gallery-photo-$page"), contentScale = ContentScale.Fit, placeholder = placeholder,
            onLoading = { failed = false; loaded = false },
            onError = { failed = true; loaded = false },
            onSuccess = { result ->
                loaded = true
                failed = false
                val decoded = result.result.image
                if (decoded.width > 0 && decoded.height > 0) transform.decodedRatio(decoded.width.toFloat() / decoded.height)
                onPainter(result.painter)
                val drawable = decoded.asDrawable(resources) as? Animatable
                animation = drawable
                if (!active || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) drawable?.stop()
            })
        }
        if (!loaded && !failed && placeholder == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Color.White, strokeWidth = 2.dp)
        if (failed) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("图片加载失败", color = Color.White)
            TextButton(onClick = { retry++ }, enabled = enabled) { Text("重试", color = Color.White) }
        }
    }
}
