package com.kudo.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kudo.android.ui.components.liquid.LiquidButton
import com.kudo.android.ui.theme.LocalDisplaySettings
import com.kudo.android.ui.topic.LocalReadingBlocked
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val LocalQuickScrollBottomPadding = staticCompositionLocalOf { 12.dp }
private val QuickScrollButtonSize = 32.dp
private val ScrollTopIcon = ImageVector.Builder("ScrollTop", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 4f); lineTo(19f, 4f); lineTo(19f, 6f); lineTo(5f, 6f); close()
        moveTo(5f, 14f); lineTo(7f, 16f); lineTo(12f, 11f)
        lineTo(17f, 16f); lineTo(19f, 14f); lineTo(12f, 7f); close()
    }
}.build()
private val LocalQuickScrollTargets = staticCompositionLocalOf<MutableList<QuickScrollTarget>?> { null }

private class QuickScrollTarget {
    var bounds by mutableStateOf(Rect.Zero)
    var enabled by mutableStateOf(false)
    var scrolled by mutableStateOf(false)
    var bottomPadding by mutableStateOf(12.dp)
    var scrollToTop: suspend () -> Unit = {}
}

@Composable
internal fun QuickScrollToTopHost(content: @Composable () -> Unit) {
    val targets = remember { mutableStateListOf<QuickScrollTarget>() }
    val backdrop = rememberLayerBackdrop()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val settings = LocalDisplaySettings.current
    val ime = WindowInsets.ime.getBottom(density)
    val navigation = WindowInsets.navigationBars.getBottom(density)
    val target = targets.filter { it.enabled && it.bounds.width > 0 && it.bounds.height > 0 }
        .maxByOrNull { it.bounds.width * it.bounds.height }
    Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInWindow() }) {
        CompositionLocalProvider(LocalQuickScrollTargets provides targets) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
        }
        if (target != null) key(target) {
            val scope = rememberCoroutineScope()
            var scrolling by remember { mutableStateOf(false) }
            val size = with(density) { QuickScrollButtonSize.toPx() }
            val bottom = minOf(target.bounds.bottom - with(density) { target.bottomPadding.toPx() },
                bounds.bottom - navigation - with(density) { 12.dp.toPx() })
            val x = target.bounds.center.x - bounds.left - size / 2
            val y = bottom - bounds.top - size
            AnimatedVisibility(settings.showScrollToTop && target.scrolled && ime == 0 && y >= 0,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                enter = fadeIn(), exit = fadeOut()) {
                LiquidButton(onClick = {
                    if (!scrolling) scope.launch {
                        scrolling = true
                        try { target.scrollToTop() } finally { scrolling = false }
                    }
                }, backdrop = backdrop, mode = settings.glass, enabled = !scrolling,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .3f),
                    contentPadding = PaddingValues(0.dp), modifier = Modifier.size(QuickScrollButtonSize).testTag("quick-scroll-to-top")) {
                    Icon(ScrollTopIcon, "回到顶部", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun Modifier.quickScrollTarget(scrolled: Boolean, enabled: Boolean = true,
    bottomPadding: Dp = LocalQuickScrollBottomPadding.current, scrollToTop: suspend () -> Unit): Modifier {
    val targets = LocalQuickScrollTargets.current ?: return this
    val target = remember(targets) { QuickScrollTarget() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val blocked = LocalReadingBlocked.current
    SideEffect {
        target.enabled = enabled && !blocked && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        target.scrolled = scrolled
        target.bottomPadding = bottomPadding
        target.scrollToTop = scrollToTop
    }
    DisposableEffect(targets, target) {
        targets += target
        onDispose { targets -= target }
    }
    return onGloballyPositioned { target.bounds = it.boundsInWindow() }
}

@Composable
internal fun Modifier.quickScrollTarget(state: LazyListState, enabled: Boolean = true, extraScrolled: Boolean = false,
    bottomPadding: Dp = LocalQuickScrollBottomPadding.current, expandHeader: suspend () -> Unit = {}): Modifier {
    val scrolled by remember(state) { derivedStateOf { state.canScrollBackward } }
    return quickScrollTarget(scrolled || extraScrolled, enabled, bottomPadding) {
        coroutineScope { launch { state.animateScrollToItem(0) }; launch { expandHeader() } }
    }
}

@Composable
internal fun Modifier.quickScrollTarget(state: LazyGridState, enabled: Boolean = true): Modifier {
    val scrolled by remember(state) { derivedStateOf { state.canScrollBackward } }
    return quickScrollTarget(scrolled, enabled) { state.animateScrollToItem(0) }
}

@Composable
internal fun Modifier.quickScrollTarget(state: ScrollState, enabled: Boolean = true): Modifier {
    val scrolled by remember(state) { derivedStateOf { state.value > 0 } }
    return quickScrollTarget(scrolled, enabled) { state.animateScrollTo(0) }
}
