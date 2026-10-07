package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FeedRepositoryTest {
    @Test fun newCountUsesTrackingStatesAndPreservesArrayRedirectsAndFailures() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            val user = Member(1, "a b", "测试账号", "")
            val states = """[
                {"topic_id":1,"last_read_post_number":null,"notification_level":2,"created_in_new_period":true},
                {"topic_id":1,"last_read_post_number":null,"notification_level":2,"created_in_new_period":true},
                {"topic_id":2,"last_read_post_number":null,"notification_level":null,"created_in_new_period":true},
                {"topic_id":3,"last_read_post_number":null,"notification_level":0,"created_in_new_period":true},
                {"topic_id":4,"last_read_post_number":null,"notification_level":1,"created_in_new_period":true},
                {"topic_id":5,"last_read_post_number":1,"highest_post_number":3,"notification_level":2},
                {"topic_id":6,"last_read_post_number":null,"created_in_new_period":false},
                {"topic_id":7,"last_read_post_number":null,"created_in_new_period":true,"deleted":true},
                {"topic_id":8,"last_read_post_number":null,"created_in_new_period":true,"is_seen":true}
            ]"""
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/tracking.json"))
            server.enqueue(MockResponse().setBody(states))
            assertEquals(2, repository.newTopicCount(user))
            assertEquals("/u/a%20b/topic-tracking-state.json", server.takeRequest().path)
            assertEquals("/tracking.json", server.takeRequest().path)
            server.enqueue(MockResponse().setBody(states))
            assertEquals(3, repository.newTopicCount(user.copy(unifiedNew = true)))
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            assertEquals(FailureKind.SIGN_IN, (runCatching { repository.newTopicCount(user) }.exceptionOrNull() as ApiFailure).kind)
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(runCatching { repository.newTopicCount(user) }.isFailure)
        } finally { server.shutdown() }
    }

    @Test fun personalListsUseTheirOwnEndpointsAndServerPagination() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            for (filter in listOf("posted", "read", "bookmarks")) {
                server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[{"id":12,"title":"个人话题"}],"more_topics_url":"/$filter.json?page=1"}}"""))
                val first = repository.topics(filter)
                assertEquals("/$filter.json", server.takeRequest().path)
                assertEquals(12, first.topics.single().id)
                server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[{"id":13,"title":"下一页"}]}}"""))
                assertEquals(13, repository.topics(filter, next = first.next).topics.single().id)
                assertEquals("/$filter.json?page=1", server.takeRequest().path)
            }
        } finally { server.shutdown() }
    }
}
