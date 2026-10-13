package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.account.LikedPostsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class LikedPostsViewModelTest {
    private fun action(id: Int, floor: Int = 8) =
        """{"id":$id,"topic_id":99,"post_number":$floor,"title":"点赞内容","action_type":1}"""
    private fun page(rows: List<String>) = MockResponse().setBody("""{"user_actions":[${rows.joinToString(",")}]}""")
    private suspend fun TestScope.finish(vm: LikedPostsViewModel) {
        runCurrent()
        vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
    }

    @Test fun usesCurrentAccountGivenLikesAndRawOffsetWithoutMergingDifferentFloors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(List(29) { action(1) } + "{}"))
            server.enqueue(page(listOf(action(1), action(2, 9))))
            server.enqueue(page(listOf(action(3, 10))))
            val vm = LikedPostsViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "current-member")
            try {
                finish(vm)
                assertEquals(1, vm.state.value.entries.size)
                assertEquals("30", vm.state.value.next)
                vm.load(true); finish(vm)
                assertEquals(listOf(8, 9), vm.state.value.entries.map { it.number })
                assertEquals("https://linux.do/t/topic/99/9", vm.state.value.entries.last().url)
                assertNull(vm.state.value.next)
                vm.load(); finish(vm)
                assertEquals(listOf(10), vm.state.value.entries.map { it.number })
                val requests = List(3) { server.takeRequest() }
                assertEquals(listOf("0", "30", "0"), requests.map { it.requestUrl!!.queryParameter("offset") })
                requests.forEach {
                    assertEquals("GET", it.method)
                    assertEquals("/user_actions.json", it.requestUrl!!.encodedPath)
                    assertEquals("current-member", it.requestUrl!!.queryParameter("username"))
                    assertEquals("1", it.requestUrl!!.queryParameter("filter"))
                }
                assertEquals(3, server.requestCount)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun retriesFailedPaginationAndRefreshUsingTheirOwnCursorsWhileKeepingEntries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(List(30) { action(it + 1) }))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(page(List(30) { action(it + 31) }))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(page(listOf(action(100, 20))))
            val vm = LikedPostsViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "member")
            try {
                finish(vm)
                vm.load(true); finish(vm)
                assertNotNull(vm.state.value.error)
                assertEquals(30, vm.state.value.entries.size)
                assertEquals("30", vm.state.value.next)
                vm.retry(); finish(vm)
                assertNull(vm.state.value.error)
                assertEquals(60, vm.state.value.entries.size)
                vm.load(); finish(vm)
                assertNotNull(vm.state.value.error)
                assertEquals(60, vm.state.value.entries.size)
                vm.retry(); finish(vm)
                assertEquals(listOf(20), vm.state.value.entries.map { it.number })
                assertNull(vm.state.value.error)
                assertEquals(listOf("0", "30", "30", "0", "0"),
                    List(5) { server.takeRequest().requestUrl!!.queryParameter("offset") })
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun refreshDiscardsDelayedResponseAndSeparateAccountKeepsItsOwnFeed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(listOf(action(1, 2))).setBodyDelay(500, TimeUnit.MILLISECONDS))
            server.enqueue(page(listOf(action(2, 3))))
            server.enqueue(page(listOf(action(3, 4))))
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            val vm = LikedPostsViewModel(repository, "first")
            var other: LikedPostsViewModel? = null
            try {
                runCurrent()
                assertEquals("first", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("username"))
                vm.load(); finish(vm)
                assertEquals(listOf(3), vm.state.value.entries.map { it.number })
                other = LikedPostsViewModel(repository, "second")
                finish(other)
                assertEquals(listOf(4), other.state.value.entries.map { it.number })
                assertEquals(listOf(3), vm.state.value.entries.map { it.number })
                assertEquals("first", server.takeRequest().requestUrl!!.queryParameter("username"))
                assertEquals("second", server.takeRequest().requestUrl!!.queryParameter("username"))
            } finally { vm.viewModelScope.cancel(); other?.viewModelScope?.cancel(); Dispatchers.resetMain() }
        }
    }

    @Test fun emptyPageStopsPaginationAndRemainsSuccessfulEmptyState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.enqueue(page(emptyList()))
            val vm = LikedPostsViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "member")
            try {
                finish(vm)
                vm.load(true); runCurrent()
                assertTrue(vm.state.value.loaded)
                assertFalse(vm.state.value.loading)
                assertTrue(vm.state.value.entries.isEmpty())
                assertNull(vm.state.value.next)
                assertNull(vm.state.value.error)
                assertEquals(1, server.requestCount)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }
}
