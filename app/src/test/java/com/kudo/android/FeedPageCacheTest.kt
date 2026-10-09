package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.feed.FeedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class FeedPageCacheTest {
    @Test fun revisitingPagesKeepsPaginationWithoutRequestsAndAccountSwitchClearsAllCaches() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val paths = CopyOnWriteArrayList<String>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path!!
                    val body = when {
                        request.path == "/site.json" -> """{"categories":[{"id":1,"name":"分类"}]}"""
                        request.path!!.contains("topic-tracking") -> "[]"
                        request.path == "/latest.json" -> """{"topic_list":{"topics":[{"id":1,"title":"最新"}],"more_topics_url":"/latest.json?page=1"}}"""
                        request.path!!.contains("page=1") -> """{"topic_list":{"topics":[{"id":2,"title":"第二页"}]}}"""
                        else -> """{"topic_list":{"topics":[{"id":3,"title":"另一个筛选"}]}}"""
                    }
                    return MockResponse().setBody(body)
                }
            }
            server.start()
            val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))))
            suspend fun idle() = withContext(Dispatchers.Default) { withTimeout(5000) { vm.viewModelScope.coroutineContext.job.children.toList().joinAll() } }
            try {
                idle(); vm.more(); idle()
                vm.select("hot"); idle()
                val count = paths.size
                vm.select("latest"); idle()
                assertEquals(count, paths.size)
                assertEquals(listOf(1, 2), vm.state.value.topics.map(Topic::id))
                assertNull(vm.state.value.next)
                assertTrue(vm.pages.value.containsKey("hot"))
                vm.setUser(Member(7, "seven", "用户七", "")); idle()
                assertTrue(vm.pages.value.isEmpty())
                assertEquals("unread", vm.state.value.filter)
                vm.select("bookmarks"); idle()
                vm.setUser(null); idle()
                assertTrue(vm.pages.value.isEmpty())
                assertEquals("latest", vm.state.value.filter)
                assertFalse(paths.contains("/read.json"))
            } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin(); Dispatchers.resetMain() }
        }
    }
}
