package com.kudo.android

import com.kudo.android.data.*
import com.kudo.android.ui.feed.FeedViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @Test fun manualRefreshReportsNewAndUpdatedTopicsFromTheResponse() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val server = MockWebServer()
        var feedRequests = 0
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when {
                request.path == "/site.json" -> MockResponse().setBody("""{"categories":[]}""")
                feedRequests++ == 0 -> MockResponse().setBody("""{"topic_list":{"topics":[{"id":1,"title":"旧话题","posts_count":1}]}}""")
                else -> MockResponse().setBody("""{"topic_list":{"topics":[{"id":1,"title":"旧话题","posts_count":2},{"id":2,"title":"新话题","posts_count":1}]}}""")
            }
        }
        server.start()
        val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))))
        try {
            withTimeout(5000) { while (vm.state.value.loading) delay(10) }
            val event = async { vm.refreshEvents.first() }
            vm.refresh()
            assertEquals(2, withTimeout(5000) { event.await() })
        } finally {
            vm.viewModelScope.cancel()
            server.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test fun categoryRateLimitDoesNotReplaceSuccessfulFeedWithError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"users":[],"topic_list":{"topics":[{"id":42,"title":"正常话题"}]}}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"errors":["分类请求过快"]}"""))
        val vm = FeedViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))))
        try {
            vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
            assertEquals(listOf(42), vm.state.value.topics.map { it.id })
            assertFalse(vm.state.value.loading)
            assertNull("辅助分类请求失败不能遮挡已成功加载的话题", vm.state.value.error)
        } finally { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    }
}

object NoCookies : SessionCookies {
    override fun get(url: String) = ""
    override fun set(url: String, cookie: String) = Unit
}
