package com.kudo.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import com.kudo.android.data.*
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReplyPreviewImagesUiTest {
    @get:Rule val compose = createComposeRule()
    private val author = Member(2, "reply_author", "回复者", "", avatarDetailsLoaded = true)
    private val root = Post(20, 2, author, "<p>原评论</p>", "", 0, false, null, null, true)
    private fun image(name: String) = """<div class="lightbox-wrapper"><a class="lightbox" href="/uploads/original/$name.png"><img src="/uploads/optimized/$name.png" alt="image" width="789" height="399"><div class="meta"><span class="filename">image</span><span class="informations">789×399 28.5 KB</span></div></a></div>"""

    @Test fun imageOnlyPreviewOpensOriginalWithoutJumpingToTheFloor() {
        val gallery = ImageGalleryState()
        var jumped: Int? = null
        val post = root.copy(id = 30, number = 3, replyTo = 2, cooked = image("first"))
        assertEquals(1, parseArticle(post.cooked).articleImages().size)
        compose.setContent { KudoTheme(DisplaySettings()) {
            CompositionLocalProvider(LocalImageGallery provides gallery) {
                ReplyPreview(PostThread(root, listOf(post)), null, false, true) { jumped = it }
            }
        } }
        compose.onNodeWithText("回复者：", substring = true).assertIsDisplayed()
        compose.onNodeWithText("789×399", substring = true).assertDoesNotExist()
        compose.onNodeWithText("28.5 KB", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("reply-preview-images-30", useUnmergedTree = true)
            .assertExists().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle {
            assertNull(jumped)
            assertEquals("https://linux.do/uploads/original/first.png", gallery.session!!.current.originalUrl)
            assertEquals(1, gallery.session!!.images.size)
        }
    }

    @Test fun textAndEmojiRemainAndMultiplePicturesHaveTheirOwnGallery() {
        val gallery = ImageGalleryState()
        val post = root.copy(id = 30, number = 3, replyTo = 2,
            cooked = "<p>回来看看<img class=\"emoji\" src=\"/images/emoji/twitter/smile.png\" alt=\":smile:\"></p>" + image("first") + image("second"))
        assertEquals(2, parseArticle(post.cooked).articleImages().size)
        compose.setContent { KudoTheme(DisplaySettings(theme = ThemeMode.DARK)) {
            CompositionLocalProvider(LocalImageGallery provides gallery) {
                ReplyPreview(PostThread(root, listOf(post)), null, false, true) {}
            }
        } }
        compose.onNodeWithText("回来看看", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(":smile:", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("查看图片(2)").assertIsDisplayed()
        compose.onNodeWithTag("reply-preview-images-30", useUnmergedTree = true)
            .assertExists().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle {
            assertEquals(listOf("https://linux.do/uploads/original/first.png", "https://linux.do/uploads/original/second.png"),
                gallery.session!!.images.map { it.originalUrl })
            gallery.session!!.select(1)
            assertTrue(gallery.session!!.current.originalUrl.endsWith("second.png"))
        }
    }

    @Test fun ignoredReplyDoesNotExposeAnImageLink() {
        val post = root.copy(id = 30, number = 3, replyTo = 2, cooked = image("private"))
        compose.setContent { KudoTheme(DisplaySettings()) {
            CompositionLocalProvider(LocalIgnoredUsers provides setOf(author.username)) {
                ReplyPreview(PostThread(root, listOf(post)), null, false, true) {}
            }
        } }
        compose.onNodeWithText("回复者：", substring = true).assertIsDisplayed()
        compose.onNodeWithText("内容已忽略").assertIsDisplayed()
        compose.onNodeWithTag("reply-preview-images-30", useUnmergedTree = true).assertDoesNotExist()
    }
}
