package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.topic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadAndDecorationTest {
    private val author = Member(1, "author", "作者", "")
    private fun post(n: Int, parent: Int? = null) = Post(n, n, author, "正文$n", "", 0, false, null, parent, false)
    private fun json(n: Int, parent: Int? = null, count: Int = 0) = """{"id":$n,"post_number":$n,"user_id":1,"username":"author","cooked":"<p>正文$n</p>","reply_to_post_number":$parent,"reply_count":$count}"""
    private fun topic(vararg posts: String) = """{"id":5,"title":"回复话题","post_stream":{"stream":[1,2,3,4],"posts":[${posts.joinToString()}]}}"""

    @Test fun explicitParentsFlattenDescendantsAndLeaveMissingOrInvalidParentsVisible() {
        val threads = postThreads(listOf(post(1), post(2, 1), post(3, 2), post(4, 3), post(5), post(6, 99), post(7, 7), post(3, 2)))
        assertEquals(listOf(1, 2, 5, 6, 7), threads.map { it.root.number })
        assertEquals(listOf(3, 4), threads[1].replies.map(Post::number))
        assertEquals(7, threads.sumOf { 1 + it.replies.size })
    }

    @Test fun parserKeepsIndependentDecorationsAndSignatureFormats() {
        val member = DiscourseParser.member(JSONObject("""{"id":1,"username":"a","bio_cooked":"简介","profile_background_upload_url":"/profile.png","card_background_upload_url":"/card.png"}"""))
        assertEquals("/profile.png", member.profileBackground)
        assertEquals("/card.png", member.cardBackground)
        val post = DiscourseParser.post(JSONObject(json(2, 1, 3)).put("user_signature", "https://image.test/a.gif"))
        assertEquals(3, post.replyCount)
        assertEquals("https://image.test/a.gif", (signatureBlocks(post.signature).single() as ArticleBlock.Image).url)
        assertTrue(signatureBlocks("<script>bad()</script>").isEmpty())
        assertTrue(signatureBlocks("<p>文字签名</p><img src='/signature.png'>").any { it is ArticleBlock.Image })
        assertEquals("", DiscourseParser.post(JSONObject(json(2)).put("user_signature", JSONObject())).signature)
        assertFalse(DisplaySettings().showSignatures)
    }

    @Test fun deepFloorResolvesAncestorsWithoutReplacingTheMainReadingStream() = runTest {
        withVm(number = 4, dispatch = { request -> when (request.path) {
            "/t/5/4.json" -> topic(json(4, 3))
            "/t/5/3.json" -> topic(json(3, 2))
            "/t/5/2.json" -> topic(json(2, 1))
            else -> null
        } }) { vm ->
            val state = vm.await { !it.loading && !it.contextLoading && it.related.size == 2 }
            assertEquals(listOf(4), state.detail!!.posts.map(Post::number))
            assertEquals(2, state.threads.single().root.number)
            assertEquals(listOf(3, 4), state.threads.single().replies.map(Post::number))
        }
    }

    @Test fun replyCursorUsesUnfilteredFloorsAndLoadsDeepChildrenWithoutDuplicates() = runTest {
        val paths = CopyOnWriteArrayList<String>()
        withVm(dispatch = { request ->
            paths += request.path!!
            when (request.path) {
                "/t/5.json" -> topic(json(1), json(2, 1, 25))
                "/posts/2/replies.json?after=1" -> (3..22).joinToString(prefix = "[", postfix = "]") { json(it, if (it == 3) 2 else 1, if (it == 3) 1 else 0) }
                "/posts/2/replies.json?after=22" -> "[${json(23, 2)}]"
                "/posts/3/replies.json?after=1" -> "[${json(24, 3)}]"
                else -> null
            }
        }) { vm ->
            val root = vm.await { !it.loading }.detail!!.posts.last()
            vm.loadReplies(root, true)
            vm.await { it.replyLoads[root.id]?.loading == false }
            assertEquals(listOf(3), vm.state.value.threads.last().replies.map(Post::number))
            vm.loadReplies(root)
            vm.await { it.replyLoads[root.id]?.loading == false }
            vm.loadReplies(root)
            val state = vm.await { it.replyLoads[root.id]?.loading == false }
            assertEquals(listOf(3, 23, 24), state.threads.last().replies.map(Post::number))
            assertFalse(state.hasMoreReplies(root))
            assertTrue(paths.contains("/posts/2/replies.json?after=22"))
        }
    }

    @Test fun failedThreadCanRetryAndMissingAncestorDoesNotHideTheReply() = runTest {
        var attempts = 0
        withVm(dispatch = { request -> when (request.path) {
            "/t/5.json" -> topic(json(1), json(2, 1, 1), json(4, 3))
            "/posts/2/replies.json?after=1" -> if (++attempts == 1) null else "[${json(3, 2)}]"
            else -> null
        } }) { vm ->
            val state = vm.await { !it.loading && !it.contextLoading && it.contextError != null }
            assertTrue(state.threads.any { it.root.number == 4 })
            val root = state.detail!!.posts.first { it.number == 2 }
            vm.loadReplies(root)
            assertNotNull(vm.await { it.replyLoads[2]?.error != null }.replyLoads[2]!!.error)
            vm.loadReplies(root)
            val retried = vm.await { it.replyLoads[2]?.loading == false && it.replyLoads[2]?.error == null }
            assertEquals(listOf(3, 4), retried.threads.first { it.root.number == 2 }.replies.map(Post::number))
            assertNull("已补齐父帖后移除过时的关系错误", retried.contextError)
        }
    }

    private suspend fun TopicViewModel.await(predicate: (TopicState) -> Boolean) = withContext(Dispatchers.Default) { withTimeout(8_000) { state.first(predicate) } }
    private suspend fun TestScope.withVm(number: Int? = null, dispatch: (RecordedRequest) -> String?, block: suspend (TopicViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = dispatch(request) ?: when {
                        request.path!!.startsWith("/u/") -> """{"user":{"id":1,"username":"author"}}"""
                        request.path!!.contains("/posts.json") -> """{"post_stream":{"posts":[${json(1)}]}}"""
                        else -> return MockResponse().setResponseCode(503).setBody("{}")
                    }
                    return MockResponse().setBody(body)
                }
            }
            server.start()
            val vm = TopicViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), 5, number)
            try { block(vm) } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
}
