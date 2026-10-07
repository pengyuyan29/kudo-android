package com.kudo.android

import com.kudo.android.data.*
import com.kudo.android.ui.feed.FeedViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
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
