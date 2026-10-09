package com.kudo.android

import com.kudo.android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ImageExportTest {
    @get:Rule val folder = TemporaryFolder()
    private val gif = "GIF89a".toByteArray() + ByteArray(100) { it.toByte() }
    private fun client(block: (Request) -> ByteArray = { gif }) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(block(chain.request()).toResponseBody("application/octet-stream".toMediaType())).build()
    }.build()

    @Test fun originalAnimationBytesAndContentBasedExtensionArePreserved() = runTest {
        val image = ImageDownloader(client(), folder.root).download(OriginalImage("https://image.test/photo.jpg", "../../wrong.jpg"))
        assertEquals("image/gif", image.mimeType)
        assertTrue(image.displayName.endsWith(".gif"))
        assertFalse(image.displayName.contains('/'))
        assertArrayEquals(gif, image.file.readBytes())
    }

    @Test fun webpageAndOversizedResponsesDoNotLeaveFiles() = runTest {
        for (content in listOf("<html>verification</html>".toByteArray(), gif)) {
            try {
                ImageDownloader(client { content }, folder.root, maxBytes = 32).download(OriginalImage("https://image.test/test"))
                fail("Invalid response accepted")
            } catch (_: IOException) { }
            assertTrue(folder.root.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun partialBatchContinuesAfterDownloadAndStorageFailures() = runTest {
        val stored = mutableListOf<ByteArray>()
        val progress = mutableListOf<Int>()
        var attempts = 0
        val downloader = ImageDownloader(client { request -> if (request.url.encodedPath == "/2") "<html>fail</html>".toByteArray() else gif }, folder.root)
        val result = ImageSaver(downloader, ImageStore {
            attempts++
            if (attempts == 2) throw IOException("disk full")
            stored += it.file.readBytes()
        }).save((1..4).map { OriginalImage("https://image.test/$it") }) { count, _ -> progress += count }
        assertEquals(ImageSaveResult(2, 4), result)
        assertEquals(listOf(1, 2, 3, 4), progress)
        assertEquals(2, stored.size)
        stored.forEach { assertArrayEquals(gif, it) }
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationDoesNotPublishOrLeaveDownloadedFiles() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val downloader = ImageDownloader(client { entered.countDown(); release.await(5, TimeUnit.SECONDS); gif }, folder.root)
        var stored = false
        val task = launch(Dispatchers.Default) { ImageSaver(downloader, ImageStore { stored = true }).save(listOf(OriginalImage("https://image.test/test"))) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        task.cancel()
        release.countDown()
        task.join()
        assertFalse(stored)
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }
}
