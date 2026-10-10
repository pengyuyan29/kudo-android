package com.kudo.android

import androidx.lifecycle.viewModelScope
import com.kudo.android.data.*
import com.kudo.android.ui.account.UserProfileViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {
    @Test fun partialFailurePreservesProfileAndTabsAreLazyAndCached() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                    "/u/user.json" -> MockResponse().setBody("""{"user":{"id":1,"username":"user"}}""")
                    "/u/user/card.json" -> MockResponse().setResponseCode(500).setBody("{}")
                    "/u/user/summary.json" -> MockResponse().setBody("""{"user_summary":{"likes_received":7}}""")
                    "/user_actions.json" -> MockResponse().setBody("""{"user_actions":[{"topic_id":9,"post_number":8,"title":"回复原楼层"}]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val vm = UserProfileViewModel(CommunityRepository(DiscourseClient(NoCookies, server.url("/"))), "user", null)
            try {
                runCurrent(); vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
                assertEquals("user", vm.state.value.profile!!.member.username)
                assertNotNull(vm.state.value.cardError)
                assertEquals(7, vm.state.value.summary!!.counts["likes_received"])
                assertEquals(3, server.requestCount)
                vm.load(ProfileTab.REPLIES); runCurrent(); vm.viewModelScope.coroutineContext.job.children.toList().joinAll()
                assertEquals(8, vm.state.value.feeds[ProfileTab.REPLIES]!!.entries.single().number)
                vm.load(ProfileTab.REPLIES); runCurrent()
                assertEquals(4, server.requestCount)
                vm.follow(); runCurrent()
                assertEquals("匿名用户不能发送关注写请求", 4, server.requestCount)
            } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }
    @Test fun accountScopesAndRefreshCancelOldRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        MockWebServer().use { server ->
            server.start()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path!!.endsWith("card.json") -> MockResponse().setBody("""{"user":{"card_background_upload_url":""}}""")
                    request.path!!.endsWith("summary.json") -> MockResponse().setBody("""{"user_summary":{}}""")
                    else -> MockResponse().setBody("""{"user":{"id":1,"username":"user"}}""")
                }
            }
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            val first = UserProfileViewModel(repository, "user", 1)
            val second = UserProfileViewModel(repository, "user", 2)
            try {
                runCurrent(); first.viewModelScope.coroutineContext.job.children.toList().joinAll()
                second.viewModelScope.coroutineContext.job.children.toList().joinAll()
                assertNotSame(first.state, second.state)
                first.viewModelScope.cancel()
                second.refresh(); runCurrent(); second.viewModelScope.coroutineContext.job.children.toList().joinAll()
                assertEquals(2, second.viewerId)
                assertNull(second.state.value.error)
                assertFalse(second.state.value.loading)
            } finally { first.viewModelScope.cancel(); second.viewModelScope.cancel(); Dispatchers.resetMain() }
        }
    }
}
