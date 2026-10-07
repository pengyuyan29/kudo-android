package com.kudo.android.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kudo.android.data.GlassMode
import com.kudo.android.ui.components.liquid.LiquidBottomTab
import com.kudo.android.ui.components.liquid.LiquidBottomTabs
import com.kudo.android.ui.components.liquid.LocalLiquidBottomTabSampling
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
    LiquidBottomTabs({ selected }, onSelect, backdrop, mode, labels.size, actionIndex = 2, modifier) { previewIndex ->
        val sampling = LocalLiquidBottomTabSampling.current
        labels.forEachIndexed { index, label ->
            val chosen = selected == index
            val highlighted = sampling || chosen || previewIndex == index
            LiquidBottomTab(selected = chosen, onClick = { if (index == 2) onCompose() else onSelect(index) }) {
                if (index == 2) {
                    Box(Modifier.size(width = 44.dp, height = 34.dp).background(LogoYellow, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icons[index], if (sampling) null else "发布话题", Modifier.size(24.dp), tint = Ink)
                    }
                } else {
                    val color = if (highlighted) LogoYellow else MaterialTheme.colorScheme.onSurface
                    Icon(icons[index], null, Modifier.size(22.dp), tint = color)
                    Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp), color = color)
                }
            }
        }
    }
}
