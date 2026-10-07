package com.kudo.android.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kudo.android.data.Category
import com.kudo.android.ui.components.PageTopBar
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

@Composable
fun EditorScreen(vm: EditorViewModel, categories: List<Category>, categoryError: Throwable?, reloadCategories: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var categoryMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.upload(context, it) } }
    val locked = vm.sending
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        PageTopBar(if (vm.topicId == null) "发布话题" else "写回复", back) {
            Button(onClick = vm::publish, enabled = !locked && !vm.uploading && vm.draft.raw.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = LogoYellow, contentColor = Ink)) { Text(if (locked) "发布中…" else "发布") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (vm.topicId == null) {
                OutlinedTextField(vm.draft.title, { vm.edit(vm.draft.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("一个清楚、有吸引力的标题") }, enabled = !locked, maxLines = 3)
                Box {
                    OutlinedButton(onClick = { categoryMenu = true }, enabled = !locked) { Icon(Icons.Rounded.Tag, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(categories.firstOrNull { it.id == vm.draft.categoryId }?.name ?: "选择发布分类"); Icon(Icons.Rounded.ExpandMore, null) }
                    DropdownMenu(categoryMenu, { categoryMenu = false }, modifier = Modifier.heightIn(max = 320.dp)) { categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { vm.edit(vm.draft.copy(categoryId = category.id)); categoryMenu = false }) } }
                }
                if (categories.isEmpty()) TextButton(onClick = reloadCategories) { Text(categoryError?.message ?: "加载可发布分类") }
            } else Text(if (vm.replyTo != null) "回复 #${vm.replyTo} 楼" else "回复话题", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(vm.draft.raw, { vm.edit(vm.draft.copy(raw = it)) }, Modifier.fillMaxWidth().heightIn(min = 290.dp), label = { Text("分享你的想法…") }, placeholder = { Text("支持 Markdown、代码块和图片。\n认真交流，让好内容发生。") }, enabled = !locked, minLines = 10)
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (vm.uploading || locked) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("草稿已自动保存在本机 · ${vm.draft.raw.length} 字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { picker.launch("image/*") }, enabled = !locked && !vm.uploading) { Icon(Icons.Rounded.AddPhotoAlternate, "上传图片") }
            IconButton(onClick = { vm.edit(vm.draft.copy(raw = vm.draft.raw + "\n```\n\n```\n")) }, enabled = !locked) { Icon(Icons.Rounded.Code, "插入代码块") }
            IconButton(onClick = { vm.edit(vm.draft.copy(raw = vm.draft.raw + "\n> ")) }, enabled = !locked) { Icon(Icons.Rounded.FormatQuote, "插入引用") }
            Spacer(Modifier.weight(1f))
            Text("Markdown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
