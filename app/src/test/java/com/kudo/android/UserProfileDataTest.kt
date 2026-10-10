package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UserProfileDataTest {
    @Test fun summaryKeepsUnknownCountsAndJoinsRepliesToSideloadedTopics() {
        val result = parseProfileSummary(JSONObject("""{"user_summary":{"likes_received":107,"topic_count":12,"post_count":58,"days_visited":null,"replies":[{"topic_id":9,"post_number":8,"like_count":4}],"topics":[{"id":10}],"most_liked_users":[{"id":2,"username":"other","count":7}]},"topics":[{"id":9,"title":"回复来源"},{"id":10,"title":"热门主题"}]}"""))
        assertEquals(107, result.counts["likes_received"])
        assertNull(result.counts["days_visited"])
        assertNull(result.counts["posts_read_count"])
        assertEquals("热门主题", result.topics.single().title)
        assertEquals("回复来源", result.replies.single().title)
        assertTrue(result.replies.single().url.endsWith("/9/8"))
        assertEquals(7, result.users["most_liked_users"]!!.single().count)
    }
    @Test fun offsetAdvancesByRawRowsEvenWhenInvalidOrDuplicateRowsAreRemoved() {
        val rows = (1..30).joinToString(",") { """{"topic_id":1,"post_number":2,"action_type":5}""" }
        val result = parseProfilePage(JSONObject("""{"user_actions":[$rows]}"""), ProfileTab.REPLIES, "30")
        assertEquals(1, result.entries.size)
        assertEquals("60", result.next)
    }
    @Test fun boostUsesNestedPostUrlAndReactionUsesItsOwnCursor() {
        val boost = parseProfilePage(JSONObject("""{"boosts":[{"id":88,"cooked":"<p>加油</p>","post":{"topic_id":9,"url":"/t/topic/9/8","topic_title":"主题"}}]}"""), ProfileTab.BOOSTS, null).entries.single()
        assertEquals(8, boost.number)
        assertEquals("加油", boost.excerpt)
        val rows = (1..20).joinToString(",") { """{"id":$it,"post":{"topic_id":9,"post_number":$it},"reaction":{"reaction_value":"heart"}}""" }
        assertEquals("20", parseProfilePage(JSONObject("""{"items":[$rows]}"""), ProfileTab.REACTIONS, null).next)
    }
    @Test fun missingCollectionsAreErrorsNotEmptySuccess() {
        assertThrows(ApiFailure::class.java) { parseProfilePage(JSONObject(), ProfileTab.BOOSTS, null) }
        assertThrows(ApiFailure::class.java) { parseProfileSummary(JSONObject()) }
    }
    @Test fun userCardAndActivityEndpointsPreserveUsernameAndPagination() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val repository = CommunityRepository(DiscourseClient(NoCookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"user":{"card_background_upload_url":"/card.png"}}"""))
            assertEquals("/card.png", repository.profileCardBackground("test.name"))
            assertEquals("/u/test.name/card.json", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"user_actions":[]}"""))
            assertNull(repository.profilePage("test.name", ProfileTab.ACTIVITY, "30").next)
            val request = server.takeRequest().requestUrl!!
            assertEquals("4,5", request.queryParameter("filter"))
            assertEquals("30", request.queryParameter("offset"))
            server.enqueue(MockResponse().setBody("""[{"id":1,"username":"a"}]"""))
            assertEquals("a", repository.profileFollows("test.name", true).single().username)
            assertEquals("/u/test.name/follow/followers", server.takeRequest().path)
        }
    }
}
