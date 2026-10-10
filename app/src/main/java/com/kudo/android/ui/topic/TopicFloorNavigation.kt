package com.kudo.android.ui.topic

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.theme.LogoYellow
import kotlin.math.roundToInt

@Composable
internal fun FloorNavigationForm(raw: String, change: (String) -> Unit, highest: Int, jumping: Boolean,
    error: String?, jump: (Int) -> Unit) {
    val number = raw.toIntOrNull()
    val valid = number != null && number in 1..highest
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("前往", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            CommunityTextField(raw, { input -> if (input.length <= 10 && input.all { it in '0'..'9' }) change(input) },
                Modifier.weight(1f).padding(vertical = 12.dp).testTag("floor-input"), singleLine = true,
                placeholder = "楼层号", enabled = !jumping, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("/ $highest", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FloorSlider((number ?: 1).coerceIn(1, highest), highest, !jumping) { change(it.toString()) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { change("1") }, enabled = !jumping) { Text("首楼") }
            TextButton(onClick = { change(highest.toString()) }, enabled = !jumping) { Text("末楼") }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        else if (!valid && raw.isNotEmpty()) Text("请输入 1 至 $highest 的楼层", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        CommunityButton(if (jumping) "正在定位…" else "跳转", { number?.let(jump) },
            Modifier.fillMaxWidth().testTag("floor-confirm"), enabled = valid && !jumping)
    }
}

@Composable
private fun FloorSlider(number: Int, highest: Int, enabled: Boolean, change: (Int) -> Unit) {
    val latestChange by rememberUpdatedState(change)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f)
    val progress = if (highest <= 1) 1f else (number - 1f) / (highest - 1f)
    Canvas(Modifier.fillMaxWidth().height(48.dp).testTag("floor-slider")
        .semantics {
            contentDescription = "选择楼层"
            progressBarRangeInfo = ProgressBarRangeInfo(number.toFloat(), 1f..highest.toFloat())
            if (!enabled) disabled()
            setProgress { target -> if (enabled) { latestChange(target.roundToInt().coerceIn(1, highest)); true } else false }
        }
        .onKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.DirectionLeft -> { latestChange((number + if (rtl) 1 else -1).coerceIn(1, highest)); true }
                Key.DirectionRight -> { latestChange((number + if (rtl) -1 else 1).coerceIn(1, highest)); true }
                else -> false
            }
        }.focusable(enabled)
        .pointerInput(highest, enabled, rtl) {
            fun select(x: Float) { if (enabled) {
                val fraction = ((x - 12.dp.toPx()) / (size.width - 24.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                latestChange((1 + (if (rtl) 1 - fraction else fraction) * (highest - 1)).roundToInt())
            } }
            detectTapGestures(onTap = { select(it.x) })
        }
        .pointerInput(highest, enabled, rtl) {
            if (enabled) detectHorizontalDragGestures(onDragStart = { point ->
                val fraction = ((point.x - 12.dp.toPx()) / (size.width - 24.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                latestChange((1 + (if (rtl) 1 - fraction else fraction) * (highest - 1)).roundToInt())
            }) { event, _ ->
                event.consume()
                val fraction = ((event.position.x - 12.dp.toPx()) / (size.width - 24.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                latestChange((1 + (if (rtl) 1 - fraction else fraction) * (highest - 1)).roundToInt())
            }
        }) {
        val start = 12.dp.toPx(); val end = size.width - start; val y = size.height / 2
        val x = start + (end - start) * (if (rtl) 1 - progress else progress)
        drawLine(track, Offset(start, y), Offset(end, y), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(LogoYellow, Offset(if (rtl) end else start, y), Offset(x, y), 4.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(LogoYellow, 10.dp.toPx(), Offset(x, y))
    }
}
