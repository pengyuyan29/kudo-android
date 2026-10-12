package com.kudo.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.kudo.android.data.*
import com.kudo.android.media.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException

class ImageExportUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun singleImageHidesBatchAndDarkLargeTextMenuRemainsReadable() {
        var multiple by mutableStateOf(false)
        val current = ArticleBlock.Image("https://image.test/1.png", "", sizeLabel = "512 KB")
        val second = current.copy(url = "https://image.test/2.png", originalUrl = "https://image.test/2.png", sizeLabel = "1 MB")
        var selected: ImageAction? = null
        var dismissed = false
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK, fontScale = 1.3f)) {
            ImageActionMenu(ImageActionTarget(current, if (multiple) listOf(current, second) else listOf(current)),
                { dismissed = true }, { selected = it })
        } }
        compose.onNodeWithText("保存原图（512 KB）").assertIsDisplayed()
        compose.onNodeWithText("保存所有图片", substring = true).assertDoesNotExist()
        compose.onNodeWithText("分享图片").performClick()
        compose.runOnIdle { assertEquals(ImageAction.SHARE, selected); multiple = true }
        compose.onNodeWithText("保存所有图片（约 1.5 MB）").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ImageAction.SAVE_ALL, selected); assertFalse(dismissed) }
        val bitmap = compose.onNodeWithTag("image-action-menu").captureToImage().asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "ui-tests").apply { mkdirs() }
        File(directory, "image-menu-dark-large.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun galleryAndSystemShareKeepAnimationBytesAndOnlyExposeTheImageCache() {
        if (Build.VERSION.SDK_INT < 29) return // Scoped-storage integration; legacy permission requires a separate device.
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("avatars/three-frame.gif").use { it.readBytes() }
        val directory = File(context.cacheDir, "image_exports").apply { mkdirs() }
        val file = File.createTempFile("export-test-", ".gif", directory).apply { writeBytes(bytes) }
        val name = "export-test-${System.nanoTime()}.gif"
        val image = OriginalImageFile(file, "image/gif", name)
        var saved: Uri? = null
        try {
            val intent = imageShareIntent(context, image)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/gif", intent.type)
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            saved = GalleryImageStore(context).insert(image)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(saved)!!.use { it.readBytes() })
            context.contentResolver.query(saved, arrayOf(MediaStore.Images.Media.IS_PENDING, MediaStore.Images.Media.RELATIVE_PATH), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)); assertTrue(cursor.getString(1).startsWith("Pictures/酷DO"))
            }
            val outside = File.createTempFile("private-test-", ".txt", context.cacheDir)
            try {
                try { FileProvider.getUriForFile(context, "${context.packageName}.image-files", outside); fail("Private cache exposed") }
                catch (_: IllegalArgumentException) { }
            } finally { outside.delete() }
        } finally { saved?.let { context.contentResolver.delete(it, null, null) }; file.delete() }
    }

    @Test fun failedGalleryWriteRemovesThePendingEntry() {
        if (Build.VERSION.SDK_INT < 29) return
        val name = "failed-export-${System.nanoTime()}.gif"
        val missing = File(context.cacheDir, "missing-${System.nanoTime()}")
        try { GalleryImageStore(context).insert(OriginalImageFile(missing, "image/gif", name)); fail("Missing file accepted") }
        catch (_: IOException) { }
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(name), null)!!.use { assertEquals(0, it.count) }
    }
}
