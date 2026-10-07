package com.kudo.android

import com.kudo.android.data.*
import com.kudo.android.ui.topic.ArticleBlock
import com.kudo.android.ui.topic.parseArticle
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
}
