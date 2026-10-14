package com.kudo.android.ui.account

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kudo.android.data.GlassMode
import com.kudo.android.network.limits.RateLimitSettings
import com.kudo.android.ui.components.PageTopBar
import com.kudo.android.ui.components.SectionLabel
import com.kudo.android.ui.components.liquid.LiquidModal
import com.kudo.android.ui.theme.LogoYellow
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private data class LimitOption(val label: String, val value: Int)

private val concurrentOptions = listOf(
    LimitOption("1 · 最保守", 1),
    LimitOption("2 · 保守", 2),
    LimitOption("3 · 推荐", 3),
    LimitOption("5 · 较快", 5),
)
private val windowCountOptions = listOf(
    LimitOption("3 · 最保守", 3),
    LimitOption("4 · 保守", 4),
    LimitOption("6 · 推荐", 6),
    LimitOption("10 · 较快", 10),
)
private val windowSecondsOptions = listOf(
    LimitOption("2 秒 · 较严", 2),
    LimitOption("3 秒 · 推荐", 3),
    LimitOption("5 秒 · 宽松", 5),
    LimitOption("10 秒 · 最宽松", 10),
)

@Composable
internal fun RateLimitSettingsScreen(settings: RateLimitSettings, back: () -> Unit) {
    val values by settings.values.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("concurrent") }
    val visibility = remember { MutableTransitionState(false) }
    val backdrop = rememberLayerBackdrop()
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    val showing = visibility.currentState || visibility.targetState
    fun show(target: String) { dialog = target; visibility.targetState = true }
    fun dismiss() { visibility.targetState = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background)
            .then(if (showing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (showing) cancelFocusChange() } }.focusGroup()) {
            PageTopBar("限流设置", back)
            LazyColumn(state = scroll, modifier = Modifier.quickScrollTarget(scroll, enabled = !showing),
                contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SectionLabel("请求节流") }
                item {
                    Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                        SettingRow("最大并发请求", "${values.maxConcurrent} 个", !showing) { show("concurrent") }
                        SettingsDivider()
                        SettingRow("窗口内请求上限", "${values.maxPerWindow} 个", !showing) { show("count") }
                        SettingsDivider()
                        SettingRow("统计窗口时长", "${values.windowSeconds} 秒", !showing) { show("window") }
                    }
                }
                item {
                    Text("限制同一站点在统计窗口内的请求数量，从源头减少服务端限流。数值越小越保守，浏览时越不易触发 429，但页面刷新可能稍慢。",
                        Modifier.padding(horizontal = 9.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { SectionLabel("自动重试") }
                item {
                    Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                        SettingRow("429 自动等待", "≤ 30 秒", false) {}
                    }
                }
                item {
                    Text("服务端返回 429 且给出等待时长时，只读请求会自动等待后重试；写操作绝不自动重放，避免重复提交。等待超过 30 秒时改为提示。",
                        Modifier.padding(horizontal = 9.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val options = when (dialog) {
            "concurrent" -> concurrentOptions
            "count" -> windowCountOptions
            else -> windowSecondsOptions
        }
        val selected = when (dialog) {
            "concurrent" -> options.indexOfFirst { it.value == values.maxConcurrent }
            "count" -> options.indexOfFirst { it.value == values.maxPerWindow }
            else -> options.indexOfFirst { it.value == values.windowSeconds }
        }
        val title = when (dialog) {
            "concurrent" -> "最大并发请求"
            "count" -> "窗口内请求上限"
            else -> "统计窗口时长"
        }
        LiquidModal(visibility, title, true, backdrop, GlassMode.LIQUID, ::dismiss,
            sheetBackground = MaterialTheme.colorScheme.background, actions = {}) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)) {
                options.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth()
                        .selectable(selected = selected == index, role = Role.RadioButton, onClick = {
                            if (!visibility.targetState) return@selectable
                            when (dialog) {
                                "concurrent" -> settings.update(maxConcurrent = option.value)
                                "count" -> settings.update(maxPerWindow = option.value)
                                else -> settings.update(windowSeconds = option.value)
                            }
                            dismiss()
                        }).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 14.dp).testTag("rate-limit-choice-$index"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            color = if (selected == index) LogoYellow else MaterialTheme.colorScheme.onSurface)
                        if (selected == index) Icon(Icons.Rounded.Check, null, Modifier.padding(start = 12.dp).size(22.dp), tint = LogoYellow)
                    }
                }
            }
        }
    }
}
