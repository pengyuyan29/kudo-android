package com.kudo.android.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kudo.android.data.GlassMode
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

@Composable
fun Modifier.glass(backdrop: Backdrop, mode: GlassMode, shape: Shape = RoundedCornerShape(50), tint: Color? = null): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val opaque = mode == GlassMode.TRANSLUCENT || Build.VERSION.SDK_INT < 31
    val overlay = tint ?: surface.copy(alpha = if (opaque) 0.94f else 0.62f)
    return this.drawBackdrop(
        backdrop = backdrop, shape = { shape },
        effects = {
            if (!opaque) blur(8.dp.toPx())
            if (mode == GlassMode.LIQUID && Build.VERSION.SDK_INT >= 33) lens(18.dp.toPx(), 28.dp.toPx(), true)
        },
        onDrawSurface = { drawRect(overlay) },
    ).border(0.7.dp, surface.copy(alpha = 0.8f), shape)
}

@Composable
private fun Modifier.navigationSurface(backdrop: Backdrop, mode: GlassMode): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val compatible = mode == GlassMode.TRANSLUCENT || Build.VERSION.SDK_INT < 31
    return drawBackdrop(
        backdrop = backdrop, shape = { RoundedCornerShape(50) },
        effects = {
            if (!compatible) blur(3.dp.toPx())
            if (mode == GlassMode.LIQUID && Build.VERSION.SDK_INT >= 33) lens(10.dp.toPx(), 16.dp.toPx(), true)
        },
        highlight = { Highlight(width = 0.7.dp, style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.65f))) },
        shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.1f)) },
        onDrawSurface = { drawRect(surface.copy(alpha = if (compatible) 0.94f else 0.38f)) },
    )
}

@Composable
fun GlassNavigation(selected: Int, onSelect: (Int) -> Unit, onCompose: () -> Unit, backdrop: Backdrop, mode: GlassMode, modifier: Modifier = Modifier) {
    val labels = listOf("首页", "分类", "发布", "消息", "我的")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.GridView, Icons.Rounded.Add, Icons.Rounded.Explore, Icons.Rounded.Person)
    val position = remember { Animatable(selected.toFloat()) }
    val selectionAnimation = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 650f) }
    val scope = rememberCoroutineScope()
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val tabsBackdrop = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val neutral = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White
    LaunchedEffect(selected) { position.animateTo(selected.toFloat(), selectionAnimation) }
    // The surface clips its own content; the outer container lets the lens protrude.
    BoxWithConstraints(modifier.height(56.dp)) {
        val slotWidth = maxWidth / 5
        val slotPixels = with(LocalDensity.current) { slotWidth.toPx() }
        val moving by remember { derivedStateOf { dragPosition != null || position.isRunning } }
        val lensProgress by animateFloatAsState(if (moving) 1f else 0f, tween(140), label = "玻璃抬升")
        val lensWidth = slotWidth - 8.dp + 36.dp * lensProgress
        val lensHeight = 48.dp + 20.dp * lensProgress
        val lensWidthPixels = with(LocalDensity.current) { lensWidth.toPx() }
        val previewSlot by remember(selected, slotPixels) {
            derivedStateOf {
                val slot = dragPosition?.let { (it / slotPixels).toInt().coerceIn(0, 4) }
                    ?: if (position.isRunning) position.value.roundToInt().coerceIn(0, 4) else selected
                slot.takeUnless { it == 2 }
            }
        }
        fun settleDrag(cancelled: Boolean) {
            val release = dragPosition ?: return
            val from = (release / slotPixels - 0.5f).coerceIn(0f, 4f)
            val slot = (release / slotPixels).toInt().coerceIn(0, 4)
            val target = if (cancelled || slot == 2) selected else slot
            scope.launch {
                position.snapTo(from)
                dragPosition = null
                if (target != selected) onSelect(target)
                else position.animateTo(target.toFloat(), selectionAnimation)
            }
        }
        fun selectionOffset(widthPixels: Float): IntOffset = IntOffset(
            ((dragPosition?.let { it / slotPixels - 0.5f }?.coerceIn(0f, 4f) ?: position.value) * slotPixels + (slotPixels - widthPixels) / 2f).roundToInt(), 0)
        Box(Modifier.fillMaxSize().layerBackdrop(tabsBackdrop).navigationSurface(backdrop, mode)) {
            Box(Modifier.align(Alignment.CenterStart).offset { selectionOffset(slotPixels - with(this) { 8.dp.toPx() }) }
                .size(slotWidth - 8.dp, 48.dp).background(neutral.copy(alpha = 0.1f * (1f - lensProgress)), CircleShape))
            Row(Modifier.fillMaxSize().pointerInput(selected) {
                detectHorizontalDragGestures(
                    onDragStart = { dragPosition = it.x },
                    onDragCancel = { settleDrag(cancelled = true) },
                    onDragEnd = { settleDrag(cancelled = false) },
                ) { change, _ -> change.consume(); dragPosition = change.position.x }
            }, verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { index, label ->
                    val chosen = selected == index
                    val highlighted = chosen || previewSlot == index
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(100), label = "导航按压")
                    Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .semantics { this.selected = chosen }
                        .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Tab, onClickLabel = label) { if (index == 2) onCompose() else onSelect(index) }
                        .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (index == 2) {
                            Box(Modifier.size(width = 44.dp, height = 34.dp).background(LogoYellow, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(icons[index], "发布话题", Modifier.size(24.dp), tint = Ink)
                            }
                        } else {
                            Icon(icons[index], null, Modifier.size(22.dp), tint = if (highlighted) LogoYellow else MaterialTheme.colorScheme.onSurface)
                            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp), color = if (highlighted) LogoYellow else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        // Record the bar and its icons first. The lens never records its own output.
        Box(Modifier.align(Alignment.CenterStart).offset { selectionOffset(lensWidthPixels) }
            .requiredSize(lensWidth, lensHeight).testTag("navigation-selection")
            .drawBackdrop(
                backdrop = lensBackdrop, shape = { RoundedCornerShape(50) },
                effects = {
                    if (mode == GlassMode.LIQUID && Build.VERSION.SDK_INT >= 33) {
                        lens(10.dp.toPx() * lensProgress, 20.dp.toPx() * lensProgress, true, true)
                    } else if (mode != GlassMode.TRANSLUCENT && Build.VERSION.SDK_INT >= 31) {
                        blur(2.dp.toPx() * lensProgress)
                    }
                },
                highlight = { Highlight(width = 1.dp, alpha = 0.25f + 0.75f * lensProgress,
                    style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.8f))) },
                shadow = { Shadow(radius = 14.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.12f), alpha = lensProgress) },
                innerShadow = { InnerShadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.08f), alpha = lensProgress) },
            ))
    }
}
