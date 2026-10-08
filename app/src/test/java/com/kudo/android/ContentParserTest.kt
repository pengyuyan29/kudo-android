package com.kudo.android

import com.kudo.android.data.*
import com.kudo.android.ui.topic.ArticleBlock
import com.kudo.android.ui.topic.parseArticle
import com.kudo.android.ui.topic.parseInlineArticle
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContentParserTest {
    @Test fun tagsAcceptNamesAndObjectsWithoutDisplayingRawJson() {
        val page = DiscourseParser.topicPage(JSONObject("""{"topic_list":{"topics":[{"id":1,"title":"标签测试","tags":["Android",{"id":1461,"name":"纯水","slug":"1461-tag"},null,{"id":2},""]}]}}"""))
        assertEquals(listOf("Android", "纯水"), page.topics.single().tags)
    }
    @Test fun topicJoinsAuthorAndNormalizesNullFields() {
        val json = JSONObject("""{"users":[{"id":9,"username":"doer","name":null,"avatar_template":"/avatar/{size}.png"}],"topic_list":{"topics":[{"id":12,"title":"测试","posts_count":4,"image_url":null,"posters":[{"user_id":9}]}],"more_topics_url":"/latest.json?page=1"}}""")
        val page = DiscourseParser.topicPage(json)
        assertEquals("doer", page.topics.single().author!!.displayName)
        assertEquals("", page.topics.single().image)
        assertEquals(3, page.topics.single().replies)
        assertEquals("/latest.json?page=1", page.next)
    }
    @Test fun detailsRetainIdsOfNotYetLoadedPosts() {
        val detail = DiscourseParser.detail(JSONObject("""{"id":1,"title":"长帖","post_stream":{"stream":[10,11,12],"posts":[{"id":10,"post_number":1,"username":"u","cooked":"<p>Hello</p>"}]}}"""))
        assertEquals(1, detail.posts.size)
        assertEquals(listOf(10, 11, 12), detail.stream)
    }
    @Test fun likedPostAndBookmarkAreRecognized() {
        val post = DiscourseParser.post(JSONObject("""{"id":7,"post_number":2,"username":"u","actions_summary":[{"id":2,"count":4,"acted":true}],"bookmarks":[{"id":90}]}"""))
        assertTrue(post.liked)
        assertEquals(4, post.likes)
        assertEquals(90, post.bookmarkId)
    }
    @Test fun richContentRetainsCodeImagesQuotesAndRejectsScripts() {
        val blocks = parseArticle("""<p>正文 <strong>加粗</strong></p><blockquote><p>引用</p></blockquote><pre><code>val x = 1
println(x)</code></pre><p><img src="/uploads/image.png" width="800" height="600" alt="图"></p><script>alert(1)</script><iframe src="javascript:alert(1)"></iframe>""")
        assertEquals(4, blocks.size)
        assertTrue((blocks[1] as ArticleBlock.TextBlock).quote)
        assertTrue((blocks[2] as ArticleBlock.Code).text.contains("\n"))
        assertEquals("https://linux.do/uploads/image.png", (blocks[3] as ArticleBlock.Image).url)
    }
    @Test fun plainTextDecodesEntities() { assertEquals("A & B", plainText("<p>A &amp; B</p>")) }

    @Test fun standaloneEmojiKeepsItsImageInsteadOfOnlyTheShortcode() {
        val html = """<img src="https://cdn.ldstatic.com/images/emoji/twemoji/distorted_face.png?v=15" class="emoji" alt=":distorted_face:">"""
        val block = parseArticle(html).single() as ArticleBlock.TextBlock
        assertTrue("表情必须保留图片来源，不能只留下 :distorted_face:", block.html.contains("distorted_face.png"))
        assertTrue(block.html.contains("class=\"emoji\""))
    }

    @Test fun inlineEmojisKeepOrderNamesLinksAndHttpsSources() {
        val content = parseInlineArticle("""前<strong>粗体</strong><img class="emoji" src="/images/emoji/twemoji/distorted_face.png?v=15" alt=":distorted_face:"><a href="https://linux.do/t/1">链接<img class="emoji" src="//cdn.ldstatic.com/custom.gif" alt=":custom:"></a>后""", Color.Blue)
        assertEquals("前粗体\uFFFC链接\uFFFC后", content.text.text)
        assertEquals(listOf(":distorted_face:", ":custom:"), content.emojis.values.map { it.name })
        assertEquals(listOf("https://linux.do/images/emoji/twemoji/distorted_face.png?v=15", "https://cdn.ldstatic.com/custom.gif"), content.emojis.values.map { it.url })
        assertTrue(content.text.spanStyles.isNotEmpty())
        assertTrue(content.text.getLinkAnnotations(0, content.text.length).isNotEmpty())
    }

    @Test fun repeatedAndQuotedEmojisStayInlineWhileOrdinaryImagesStayBlocks() {
        val blocks = parseArticle("""<blockquote><p>A<img class="emoji" src="/one.png" alt=":one:"><img class="emoji" src="/one.png" alt=":one:">B<img src="/photo.png"></p></blockquote>""")
        val text = blocks[0] as ArticleBlock.TextBlock
        assertTrue(text.quote)
        val inline = parseInlineArticle(text.html, Color.Blue)
        assertEquals("A\uFFFC\uFFFCB", inline.text.text)
        assertEquals(2, inline.emojis.size)
        assertEquals("https://linux.do/photo.png", (blocks[1] as ArticleBlock.Image).url)
    }

    @Test fun invalidEmojiSourcesKeepReadableAlternativeText() {
        val content = parseInlineArticle("""<img class="emoji" src="javascript:alert(1)" alt=":bad:"><img class="emoji" src="http://example.com/emoji.png" alt=":insecure:">""", Color.Blue)
        assertEquals(":bad::insecure:", content.text.text)
        assertTrue(content.emojis.isEmpty())
    }

    @Test fun wideScreenshotsKeepTheirActualAspectRatio() {
        val block = parseArticle("""<img src="/wide.png" width="1688" height="339">""").single() as ArticleBlock.Image
        assertEquals("宽图容器不能被比例上限撑高", 1688f / 339f, requireNotNull(block.ratio), 0.0001f)
    }

    @Test fun lightboxMetadataDoesNotBecomeBodyTextButAuthorCaptionsSurvive() {
        val blocks = parseArticle("""<div class="lightbox-wrapper"><a class="lightbox" href="/original.png"><img src="/preview.png" width="1688" height="339" alt="示例"><div class="meta"><span class="filename">20261008230932_14_506</span><span class="informations">1688×339 22.8 KB</span></div></a></div><p>我写的图片说明</p><div class="meta">正常正文</div>""")
        assertEquals("只应有图片、作者说明与普通正文", 3, blocks.size)
        assertTrue(blocks.first() is ArticleBlock.Image)
        assertEquals(listOf("我写的图片说明", "正常正文"), blocks.filterIsInstance<ArticleBlock.TextBlock>().map { it.html })
        val image = blocks.first() as ArticleBlock.Image
        assertEquals("https://linux.do/original.png", image.originalUrl)
        assertEquals("20261008230932_14_506", image.filename)
        assertEquals("1688×339", image.dimensions)
        assertEquals("22.8 KB", image.sizeLabel)
    }

    @Test fun tallImagesAndMissingOrInvalidDimensionsDoNotUseAnInventedAspectRatio() {
        val tall = parseArticle("""<img src="/tall.png" width="200" height="4000">""").single() as ArticleBlock.Image
        assertEquals(0.05f, requireNotNull(tall.ratio), 0.0001f)
        for (attributes in listOf("", "width=\"0\" height=\"100\"", "width=\"200\" height=\"-2\"", "width=\"NaN\" height=\"2\"")) {
            val image = parseArticle("<img src=\"/unknown.png\" $attributes>").single() as ArticleBlock.Image
            assertNull(image.ratio)
            assertEquals(image.url, image.originalUrl)
        }
    }

    @Test fun inlineLightboxDropsOnlyAutomaticMetadataAndRejectsInsecureOriginals() {
        val blocks = parseArticle("""<p>说明<a class="lightbox" href="javascript:alert(1)"><img src="/safe.png"><span class="meta"><span class="filename">隐藏文件名</span></span></a><img class="emoji" src="/emoji.png" alt=":ok:">保留图注</p>""")
        val text = blocks.filterIsInstance<ArticleBlock.TextBlock>().single().html
        assertFalse(text.contains("隐藏文件名"))
        assertTrue(text.contains("保留图注"))
        assertTrue(text.contains("class=\"emoji\""))
        assertEquals("https://linux.do/safe.png", blocks.filterIsInstance<ArticleBlock.Image>().single().originalUrl)
    }
}
