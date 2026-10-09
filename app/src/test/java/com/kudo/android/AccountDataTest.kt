package com.kudo.android

import com.kudo.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class AccountDataTest {
    @Test fun summaryKeepsRealCountsAndUnknownValuesSeparateFromZero() {
        val summary = parseUserSummary(JSONObject("""{"user_summary":{"days_visited":825,"posts_read_count":357012,"likes_received":0,"post_count":3102}}"""))
        assertEquals(825, summary.daysVisited)
        assertEquals(357012, summary.postsRead)
        assertEquals(0, summary.likesReceived)
        assertEquals(3102, summary.posts)
        val missing = parseUserSummary(JSONObject("""{"user_summary":{"days_visited":null,"posts_read_count":"unknown","likes_received":-1}}"""))
        assertNull(missing.daysVisited); assertNull(missing.postsRead); assertNull(missing.likesReceived); assertNull(missing.posts)
    }
    @Test(expected = ApiFailure::class) fun invalidSummaryIsAnErrorInsteadOfFakeEmptyStatistics() {
        parseUserSummary(JSONObject("""{"error":"unavailable"}"""))
    }
    @Test fun personalListsUseCreatedTopicsRatherThanParticipatedTopicsAndKeepServerPagination() = runBlocking {
        val server = MockWebServer()
        val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
        val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
        try {
            server.enqueue(MockResponse().setBody("""{"users":[],"topic_list":{"topics":[],"more_topics_url":"/topics/created-by/member.json?page=1"}}"""))
            val page = repository.personalTopics("a/b", AccountDestination.TOPICS)
            assertEquals("/topics/created-by/a%2Fb.json", server.takeRequest().path)
            assertEquals("/topics/created-by/member.json?page=1", page.next)
            server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[]}}"""))
            repository.personalTopics("member", AccountDestination.MESSAGES, mailbox = "archive")
            assertEquals("/topics/private-messages-archive/member.json", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[]}}"""))
            repository.personalTopics("member", AccountDestination.HISTORY)
            assertEquals("/read.json", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
