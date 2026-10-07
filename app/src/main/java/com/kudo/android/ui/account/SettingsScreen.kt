package com.kudo.android.ui.account

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kudo.android.BuildConfig
import com.kudo.android.data.*
import com.kudo.android.ui.components.*

@Composable
fun SettingsScreen(settings: DisplaySettings, update: (DisplaySettings) -> Unit, user: Member?, logout: () -> Unit, back: () -> Unit, openLink: (String) -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    Column {
        PageTopBar("设置", back)
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            item { SectionLabel("界面显示") }
            item {
                Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                    SettingRow("主题风格", settings.theme.label) { dialog = "theme" }
                    SettingRow("界面材质效果", settings.glass.label) { dialog = "glass" }
                    SettingRow("字体大小", if (settings.fontScale == 1f) "跟随系统" else "${(settings.fontScale * 100).toInt()}%") { dialog = "font" }
                }
            }
            item { Text(if (Build.VERSION.SDK_INT >= 33) "液态玻璃会随背景内容呈现模糊、折射和高光。可切换半透明以减少视觉效果。" else "此系统使用兼容材质显示；完整液态玻璃需要 Android 13 或更高版本。", Modifier.padding(horizontal = 9.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item { SectionLabel("关于酷DO") }
            item {
                Column(Modifier.clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface)) {
                    SettingRow("酷DO", "${BuildConfig.VERSION_NAME} · Linux.do 第三方客户端") { dialog = "about" }
                    SettingRow("开源致谢", "技术与设计参考") { dialog = "licenses" }
                    SettingRow("社区指南", "真诚 · 友善 · 团结 · 专业") { openLink("https://linux.do/guidelines") }
                    SettingRow("隐私说明", "账号与草稿仅保存在本机") { dialog = "privacy" }
                }
            }
            if (user != null) item { OutlinedButton(onClick = { dialog = "logout" }, modifier = Modifier.fillMaxWidth()) { Text("退出登录") } }
        }
    }
    when (dialog) {
        "theme" -> ChoiceDialog("主题风格", ThemeMode.entries.map { it.label }, settings.theme.ordinal, { update(settings.copy(theme = ThemeMode.entries[it])); dialog = null }, { dialog = null })
        "glass" -> ChoiceDialog("界面材质效果", GlassMode.entries.map { it.label }, settings.glass.ordinal, { update(settings.copy(glass = GlassMode.entries[it])); dialog = null }, { dialog = null })
        "font" -> ChoiceDialog("字体大小", listOf("较小 · 85%", "跟随系统", "较大 · 115%", "大号 · 130%"), listOf(0.85f, 1f, 1.15f, 1.3f).indexOf(settings.fontScale), { update(settings.copy(fontScale = listOf(0.85f, 1f, 1.15f, 1.3f)[it])); dialog = null }, { dialog = null })
        "logout" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("退出登录？") }, text = { Text("本机登录状态和编辑草稿将被清除，社区内容与收藏不会删除。") }, confirmButton = { TextButton(onClick = { dialog = null; logout() }) { Text("退出") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
        "about", "privacy", "licenses" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(when (dialog) { "privacy" -> "隐私说明"; "licenses" -> "开源致谢"; else -> "酷DO" }) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (dialog) {
                    "privacy" -> "酷DO 直接连接 Linux.do。登录在官网页面完成；应用不收集密码，不接入统计或广告服务。Cookie 与草稿保存在应用私有目录，禁用系统备份，退出登录后清除。帖子中的外部图片会向其来源站点发起请求。"
                    "licenses" -> "Jetpack Compose、Backdrop（含官方底栏示例）、Shapes、OkHttp、Coil：Apache License 2.0。Jsoup：MIT。底部玻璃导航改编自 Backdrop catalog；接口行为参考 FluxDO，页面布局参考酷安。"
                    else -> "用原生体验，连接值得交流的人与内容。\n\n酷DO 是独立的 Linux.do 第三方客户端。"
                }, style = MaterialTheme.typography.bodyMedium)
                if (dialog == "licenses") { TextButton(onClick = { openLink("https://github.com/Kyant0/AndroidLiquidGlass") }) { Text("Backdrop 项目与许可") }; TextButton(onClick = { openLink("https://github.com/Lingyan000/fluxdo") }) { Text("FluxDO 接口参考") } }
            }
        }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("知道了") } })
    }
}

@Composable
private fun SettingRow(title: String, value: String, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 18.dp, vertical = 17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), MaterialTheme.colorScheme.outline) }
        Text(value, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, choose: (Int) -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth().clickable { choose(index) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == index, { choose(index) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
