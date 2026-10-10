package com.kudo.android.ui.account

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kudo.android.BuildConfig
import com.kudo.android.R
import com.kudo.android.ui.components.PageTopBar
import com.kudo.android.ui.components.liquid.LiquidModal
import com.kudo.android.ui.components.liquid.LiquidModalAction
import com.kudo.android.ui.theme.LocalDisplaySettings
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal const val KudoAboutText = "酷DO是独立 LINUX DO 第三方安卓原生客户端"

@Composable
fun AboutScreen(openLink: (String) -> Unit = {}, back: () -> Unit) {
    val settings = LocalDisplaySettings.current
    val backdrop = rememberLayerBackdrop()
    val visibility = remember { MutableTransitionState(false) }
    var dialog by rememberSaveable { mutableStateOf("version") }
    val showing = visibility.currentState || visibility.targetState
    fun show(value: String) { dialog = value; visibility.targetState = true }
    fun dismiss() { visibility.targetState = false }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).layerBackdrop(backdrop)
            .then(if (showing) Modifier.clearAndSetSemantics {} else Modifier)
            .focusProperties { onEnter = { if (showing) cancelFocusChange() } }.focusGroup()) {
            PageTopBar("关于酷DO", back)
            LazyColumn(Modifier.testTag("about-content"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Top) {
                            Brand(R.drawable.ic_kudo, "酷DO", Modifier.weight(1f))
                            Icon(Icons.Rounded.Favorite, "热爱", Modifier.padding(top = 18.dp).size(28.dp), tint = Color(0xFFFF5B66))
                            Brand(R.drawable.ic_linux_do, "LINUX DO", Modifier.weight(1f))
                        }
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Text(KudoAboutText, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                        SettingRow("酷DO", "${BuildConfig.VERSION_NAME} · LINUX DO 第三方客户端", !showing) { show("version") }
                        SettingRow("开源致谢", "技术与设计参考", !showing) { show("licenses") }
                        SettingRow("社区指南", "真诚 · 友善 · 团结 · 专业", !showing) { openLink("https://linux.do/guidelines") }
                        SettingRow("隐私说明", "账号与草稿仅保存在本机", !showing) { show("privacy") }
                    }
                }
                item { Text("真诚 · 友善 · 团结 · 专业", Modifier.testTag("about-slogan"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            }
        }
        LiquidModal(visibility, when (dialog) { "privacy" -> "隐私说明"; "licenses" -> "开源致谢"; else -> "酷DO" },
            false, backdrop, settings.glass, ::dismiss, sheetBackground = MaterialTheme.colorScheme.background,
            actions = { LiquidModalAction("知道了", ::dismiss, Modifier.fillMaxWidth(), primary = true) }) {
            Text(when (dialog) {
                "privacy" -> "酷DO 直接连接 Linux.do。登录在官网页面完成；应用不收集密码，不接入统计或广告服务。Cookie 与草稿保存在应用私有目录，禁用系统备份，退出登录后清除。帖子中的外部图片会向其来源站点发起请求。"
                "licenses" -> "Jetpack Compose、Backdrop（含官方玻璃示例）、Shapes、OkHttp、Coil、APNG4Android：Apache License 2.0。Jsoup：MIT。分类、标签、徽章与资质图标采用 Font Awesome Free 6.7.2，© 2024 Fonticons, Inc.，CC BY 4.0；原始矢量路径不变，适配原生尺寸与颜色。底部导航与详情操作栏改编自 Backdrop catalog 的 LiquidBottomTabs / Surface Liquid Button，弹窗材质改编自官方 Dialog；接口行为参考 FluxDO，页面布局参考酷安。"
                else -> "酷DO ${BuildConfig.VERSION_NAME}\n\n$KudoAboutText"
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

@Composable
private fun Brand(logo: Int, name: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(logo), "$name Logo", Modifier.size(64.dp), tint = Color.Unspecified)
        Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
