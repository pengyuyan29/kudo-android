package com.kudo.android.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class EditorViewModel(private val app: AppContainer, userId: Int, val topicId: Int?, val replyTo: Int?) : ViewModel() {
    private val draftKey = "${userId}_${topicId ?: "new"}_${replyTo ?: 0}"
    var draft by mutableStateOf(app.settings.draft(draftKey)); private set
    var sending by mutableStateOf(false); private set
    var uploading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private val results = Channel<PublishResult>(Channel.BUFFERED)
    val published = results.receiveAsFlow()

    fun edit(value: Draft) { if (!sending) { draft = value; app.settings.saveDraft(draftKey, value); error = null } }
    fun publish() {
        if (sending || uploading) return
        if (draft.raw.isBlank()) { error = "先写点内容吧"; return }
        if (topicId == null && (draft.title.isBlank() || draft.categoryId == 0)) { error = "请填写标题并选择分类"; return }
        sending = true; error = null
        val snapshot = draft
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { app.repository.publish(snapshot.raw, snapshot.title, snapshot.categoryId.takeIf { it > 0 }, topicId, replyTo) }
                app.settings.clearDraft(draftKey)
                results.send(result)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "发布失败，草稿已保留"; sending = false }
        }
    }
    fun upload(context: Context, uri: Uri) {
        if (uploading || sending) return
        uploading = true; error = null
        viewModelScope.launch {
            try {
                val markdown = withContext(Dispatchers.IO) {
                    val resolver = context.applicationContext.contentResolver
                    val type = resolver.getType(uri) ?: throw IllegalArgumentException("无法识别图片格式")
                    val extension = when (type) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> throw IllegalArgumentException("请选择 JPG、PNG、WebP 或 GIF 图片") }
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) { require(output.size() + count <= 10 * 1024 * 1024) { "图片不能超过 10 MB" }; output.write(buffer, 0, count) }
                        output.toByteArray()
                    } ?: throw IllegalArgumentException("无法读取图片")
                    app.repository.upload(bytes, type, "kudo-${System.currentTimeMillis()}.$extension")
                }
                edit(draft.copy(raw = draft.raw + "\n\n" + markdown + "\n"))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "上传失败" }
            finally { uploading = false }
        }
    }
}
