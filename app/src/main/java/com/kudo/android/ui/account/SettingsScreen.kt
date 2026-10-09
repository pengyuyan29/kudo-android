package com.kudo.android.ui.account

import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kudo.android.BuildConfig
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import com.kudo.android.ui.components.liquid.LiquidModal
import com.kudo.android.ui.components.liquid.LiquidModalAction
import com.kudo.android.ui.theme.LogoYellow
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun SettingsScreen(settings: DisplaySettings, update: (DisplaySettings) -> Unit, user: Member?, logout: () -> Unit, back: () -> Unit, openLink: (String) -> Unit) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf("theme") }
    val visibility = remember { MutableTransitionState(false) }
    val backdrop = rememberLayerBackdrop()
    val showing = visibility.currentState || visibility.targetState
    fun show(target: String) { dialog = target; visibility.targetState = true }
    fun dismiss() { visibility.targetState = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background)
            .then(if (showing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (showing) cancelFocusChange() } }.focusGroup()) {
            PageTopBar("设置", back)
            LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                item { SectionLabel("界面显示") }
                item {
                    Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                        SettingRow("主题风格", settings.theme.label, !showing) { show("theme") }
                        SettingRow("界面材质效果", settings.glass.label, !showing) { show("glass") }
                        SettingRow("字体大小", if (settings.fontScale == 1f) "跟随系统" else "${(settings.fontScale * 100).toInt()}%", !showing) { show("font") }
                        SettingRow("主题列表样式", settings.topicListStyle.label, !showing) { show("list") }
                    }
                }
                item { Text(if (Build.VERSION.SDK_INT >= 33) "液态玻璃会随背景内容呈现模糊、折射和高光。可切换半透明以减少视觉效果。" else "此系统使用兼容材质显示；完整液态玻璃需要 Android 13 或更高版本。", Modifier.padding(horizontal = 9.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                item { SectionLabel("链接打开") }
                item {
                    Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                        SettingRow("Linux.do 站内链接", "在系统中允许酷DO打开支持的链接", !showing) {
                            val action = if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")))
                        }
                    }
                }
                item { SectionLabel("关于酷DO") }
                item {
                    Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                        SettingRow("酷DO", "${BuildConfig.VERSION_NAME} · Linux.do 第三方客户端", !showing) { show("about") }
                        SettingRow("开源致谢", "技术与设计参考", !showing) { show("licenses") }
                        SettingRow("社区指南", "真诚 · 友善 · 团结 · 专业", !showing) { openLink("https://linux.do/guidelines") }
                        SettingRow("隐私说明", "账号与草稿仅保存在本机", !showing) { show("privacy") }
                    }
                }
                if (user != null) item { OutlinedButton(onClick = { show("logout") }, enabled = !showing, modifier = Modifier.fillMaxWidth()) { Text("退出登录") } }
            }
        }
        val actionSheet = dialog in listOf("theme", "glass", "font", "list")
        val title = when (dialog) {
            "theme" -> "主题风格"; "glass" -> "界面材质效果"; "font" -> "字体大小"
            "list" -> "主题列表样式"
            "privacy" -> "隐私说明"; "licenses" -> "开源致谢"; "logout" -> "退出登录？"; else -> "酷DO"
        }
        LiquidModal(visibility, title, actionSheet, backdrop, settings.glass, ::dismiss,
            actions = {
                if (actionSheet) LiquidModalAction("取消", ::dismiss, Modifier.fillMaxWidth())
                else if (dialog == "logout") {
                    LiquidModalAction("取消", ::dismiss, Modifier.weight(1f))
                    LiquidModalAction("退出", { if (visibility.targetState) { dismiss(); logout() } }, Modifier.weight(1f), primary = true)
                } else LiquidModalAction("知道了", ::dismiss, Modifier.fillMaxWidth(), primary = true)
            }) {
            if (actionSheet) {
                val scales = listOf(0.85f, 1f, 1.15f, 1.3f)
                val options = when (dialog) {
                    "theme" -> ThemeMode.entries.map { it.label }
                    "glass" -> GlassMode.entries.map { it.label }
                    "list" -> TopicListStyle.entries.map { it.label }
                    else -> listOf("较小 · 85%", "跟随系统", "较大 · 115%", "大号 · 130%")
                }
                val selected = when (dialog) {
                    "theme" -> settings.theme.ordinal; "glass" -> settings.glass.ordinal; "list" -> settings.topicListStyle.ordinal; else -> scales.indexOf(settings.fontScale)
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEachIndexed { index, label ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selected == index) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else androidx.compose.ui.graphics.Color.Transparent)
                            .selectable(selected = selected == index, role = Role.RadioButton, onClick = {
                                if (!visibility.targetState) return@selectable
                                update(when (dialog) {
                                    "theme" -> settings.copy(theme = ThemeMode.entries[index])
                                    "glass" -> settings.copy(glass = GlassMode.entries[index])
                                    "list" -> settings.copy(topicListStyle = TopicListStyle.entries[index])
                                    else -> settings.copy(fontScale = scales[index])
                                })
                                dismiss()
                            }).heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 12.dp).testTag("modal-choice-$index"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (selected == index) Icon(Icons.Rounded.Check, null, Modifier.padding(start = 12.dp).size(22.dp), tint = LogoYellow)
                        }
                    }
                }
            } else {
                Text(when (dialog) {
                    "privacy" -> "酷DO 直接连接 Linux.do。登录在官网页面完成；应用不收集密码，不接入统计或广告服务。Cookie 与草稿保存在应用私有目录，禁用系统备份，退出登录后清除。帖子中的外部图片会向其来源站点发起请求。"
                    "licenses" -> "Jetpack Compose、Backdrop（含官方玻璃示例）、Shapes、OkHttp、Coil、APNG4Android：Apache License 2.0。Jsoup：MIT。分类、标签与资质图标采用 Font Awesome Free 6.7.2，© 2024 Fonticons, Inc.，CC BY 4.0；原始矢量路径不变，适配原生尺寸与颜色。底部导航与详情操作栏改编自 Backdrop catalog 的 LiquidBottomTabs / Surface Liquid Button，弹窗材质改编自官方 Dialog；接口行为参考 FluxDO，页面布局参考酷安。"
                    "logout" -> "本机登录状态和编辑草稿将被清除，社区内容与收藏不会删除。"
                    else -> "用原生体验，连接值得交流的人与内容。\n\n酷DO 是独立的 Linux.do 第三方客户端。"
                }, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
                if (dialog == "licenses") {
                    ModalLink("Backdrop 项目与许可") { openLink("https://github.com/Kyant0/AndroidLiquidGlass") }
                    ModalLink("Font Awesome 图标与许可") { openLink("https://fontawesome.com/license/free") }
                    ModalLink("APNG4Android 动图支持与许可") { openLink("https://github.com/penfeizhou/APNG4Android") }
                    ModalLink("FluxDO 接口参考") { openLink("https://github.com/Lingyan000/fluxdo") }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, enabled: Boolean, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = action).padding(horizontal = 18.dp, vertical = 17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), MaterialTheme.colorScheme.outline) }
        Text(value, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModalLink(label: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = open)
        .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
