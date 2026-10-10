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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TopicFloorNavigationTest {
    private fun post(number: Int, parent: Int? = null) = """{"id":$number,"post_number":$number,"user_id":2,"username":"author","cooked":"<p>楼层$number</p>","reply_to_post_number":$parent}"""
    private fun topic(posts: String = post(1)) = """{"id":5,"title":"跳楼","highest_post_number":100,"post_stream":{"stream":[1,20,80,100],"posts":[$posts]}}"""
    private suspend fun TopicViewModel.await(predicate: (TopicState) -> Boolean) = withContext(Dispatchers.IO) {
        withTimeout(5000) { state.first(predicate) }
    }

    @Test fun highestFloorUsesTheServerNumberInsteadOfTheStreamCount() {
        val detail = DiscourseParser.detail(JSONObject(topic()))
        assertEquals(100, detail.highestPostNumber); assertEquals(4, detail.stream.size)
        val fallback = DiscourseParser.detail(JSONObject(topic(post(80))).apply { remove("highest_post_number") })
        assertEquals(80, fallback.highestPostNumber)
    }

    @Test fun loadedAndRepeatedTargetsDoNotFetchAgainAndRefreshKeepsTheNewTarget() = runTest {
        withTopic({ null }) { vm, paths ->
            vm.await { !it.loading && it.authorProfile != null }
            val before = paths.size
            vm.jumpTo(1); vm.await { it.targetRevision == 1L }
            vm.jumpTo(1); vm.await { it.targetRevision == 2L }
            assertEquals(before, paths.size)
            vm.refresh(); vm.await { !it.loading }
            assertTrue(paths.contains("GET /t/5/1.json"))
            assertEquals(1, vm.state.value.targetNumber)
        }
    }

    @Test fun unloadedNestedTargetMergesTheWindowAndResolvesItsParentWithoutReadingWrites() = runTest {
        withTopic({ request -> when (request.path) {
            "/t/5/80.json" -> MockResponse().setBody(topic(post(80, 20)))
            "/t/5/20.json" -> MockResponse().setBody(topic(post(20)))
            else -> null
        } }) { vm, paths ->
            vm.await { !it.loading }; vm.jumpTo(80)
            val result = vm.await { it.targetNumber == 80 && !it.contextLoading }
            assertEquals(listOf(1, 20, 80), result.allPosts.map(Post::number).sorted())
            assertEquals(80, result.threads.single { it.root.number == 20 }.replies.single().number)
            assertEquals(100, result.detail!!.highestPostNumber)
            assertTrue(result.readNumbers.isEmpty()); assertTrue(paths.all { it.startsWith("GET ") })
        }
    }

    @Test fun missingAndForbiddenFloorsKeepTheCurrentContentAndExposeAnError() = runTest {
        withTopic({ request -> when (request.path) {
            "/t/5/80.json" -> MockResponse().setBody(topic(post(79)))
            "/t/5/100.json" -> MockResponse().setResponseCode(403).setBody("""{"errors":["不能访问"]}""")
            else -> null
        } }) { vm, _ ->
            val initial = vm.await { !it.loading }.detail
            vm.jumpTo(80); vm.await { !it.jumping && it.jumpError != null }
            assertEquals(initial, vm.state.value.detail); assertNull(vm.state.value.targetNumber)
            vm.jumpTo(100); vm.await { !it.jumping && it.jumpError != null }
            assertEquals(initial, vm.state.value.detail); assertNull(vm.state.value.targetNumber)
        }
    }

    @Test fun aSupersededJumpCannotReplaceTheLatestTarget() = runTest {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        withTopic({ request -> when (request.path) {
            "/t/5/80.json" -> { started.countDown(); release.await(5, TimeUnit.SECONDS); MockResponse().setBody(topic(post(80))) }
            "/t/5/100.json" -> MockResponse().setBody(topic(post(100)))
            else -> null
        } }) { vm, _ -> try {
            vm.await { !it.loading }; vm.jumpTo(80)
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            vm.jumpTo(100); vm.await { it.targetNumber == 100 && !it.jumping }
            release.countDown()
            assertEquals(100, vm.state.value.targetNumber)
            assertFalse(vm.state.value.allPosts.any { it.number == 80 })
        } finally { release.countDown() } }
    }

    @Test fun aJumpWindowThatCompletesAParentClearsTheCancelledContextLoadingState() = runTest {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        withTopic({ request -> when (request.path) {
            "/t/5.json" -> MockResponse().setBody(topic("${post(1)},${post(80, 20)}"))
            "/t/5/20.json" -> {
                if (calls.incrementAndGet() == 1) { started.countDown(); release.await(5, TimeUnit.SECONDS) }
                MockResponse().setBody(topic(post(20)))
            }
            else -> null
        } }) { vm, _ -> try {
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            vm.jumpTo(20)
            val result = vm.await { it.targetNumber == 20 && !it.jumping && !it.contextLoading }
            assertFalse("新窗口已补齐父楼层，不能残留已取消请求的加载状态", result.contextLoading)
        } finally { release.countDown() } }
    }

    private suspend fun TestScope.withTopic(response: (RecordedRequest) -> MockResponse?, block: suspend (TopicViewModel, List<String>) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val paths = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += "${request.method} ${request.path}"
                    return response(request) ?: when {
                        request.path == "/t/5.json" || request.path == "/t/5/1.json" -> MockResponse().setBody(topic())
                        request.path!!.startsWith("/u/") -> MockResponse().setBody("""{"user":{"id":2,"username":"author"}}""")
                        else -> MockResponse().setResponseCode(404).setBody("{}")
                    }
                }
            }
            server.start()
            val vm = TopicViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), 5, viewerId = 9)
            try { block(vm, paths) } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
}
