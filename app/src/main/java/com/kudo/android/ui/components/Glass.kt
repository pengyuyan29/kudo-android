package com.kudo.android.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
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
fun GlassNavigation(selected: Int, onSelect: (Int) -> Unit, onCompose: () -> Unit, backdrop: Backdrop, mode: GlassMode, modifier: Modifier = Modifier) {
    val labels = listOf("首页", "分类", "发布", "消息", "我的")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.GridView, Icons.Rounded.Add, Icons.Rounded.Explore, Icons.Rounded.Person)
    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.78f, stiffness = 380f), label = "玻璃选中态")
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier.height(72.dp).glass(backdrop, mode)) {
        val slotWidth = maxWidth / 5
        Box(Modifier.padding(5.dp).offset(x = slotWidth * position).width(slotWidth - 10.dp).fillMaxHeight()
            .glass(backdrop, mode, tint = LogoYellow.copy(alpha = 0.2f)))
        Row(Modifier.fillMaxSize().pointerInput(selected) {
            detectHorizontalDragGestures(
                onDragStart = { dragPosition = it.x },
                onDragCancel = { dragPosition = null },
                onDragEnd = {
                    val slot = ((dragPosition ?: 0f) / (size.width / 5f)).toInt().coerceIn(0, 4)
                    if (slot != 2) onSelect(slot)
                    dragPosition = null
                },
            ) { change, amount -> change.consume(); dragPosition = (dragPosition ?: change.position.x) + amount }
        }, verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                val chosen = selected == index
                Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .semantics { this.selected = chosen }
                    .clickable(role = Role.Tab, onClickLabel = label) { if (index == 2) onCompose() else onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (index == 2) {
                        Box(Modifier.size(48.dp).background(LogoYellow, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icons[index], "发布话题", Modifier.size(31.dp), tint = Ink)
                        }
                    } else {
                        Icon(icons[index], null, Modifier.size(26.dp).graphicsLayer { scaleX = if (chosen) 1.05f else 1f; scaleY = scaleX }, tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
