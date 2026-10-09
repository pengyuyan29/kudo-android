package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountNoticesTest {
    @Test fun websiteGroupsKeepMentionsLikesReactionsAndPrivateMessagesSeparate() {
        fun notice(type: Int) = DiscourseParser.notices(JSONObject("""{"notifications":[{"id":1,"notification_type":$type,"data":{"display_username":"member","title":"主题"}}]}""")).single()
        assertTrue(NoticeGroup.REPLIES.includes(notice(1)))
        assertTrue(NoticeGroup.REPLIES.includes(notice(15)))
        assertTrue(NoticeGroup.LIKES.includes(notice(19)))
        assertTrue(NoticeGroup.LIKES.includes(notice(25)))
        assertTrue(NoticeGroup.MESSAGES.includes(notice(7)))
        assertFalse(NoticeGroup.MESSAGES.includes(notice(30)))
        assertFalse(NoticeGroup.LIKES.includes(notice(12)))
        assertTrue(NoticeGroup.ALL.includes(notice(999)))
        assertEquals("member", notice(2).actor?.username)
    }
    @Test fun inaccessibleRowsDoNotPrematurelyEndPaginationAndRecentModeIsNeverUsed() = runBlocking {
        val server = MockWebServer()
        val cookies = object : SessionCookies { override fun get(url: String) = ""; override fun set(url: String, cookie: String) {} }
        try {
            val repository = CommunityRepository(DiscourseClient(cookies, server.url("/")))
            server.enqueue(MockResponse().setBody("""{"notifications":[],"total_rows_notifications":31,"load_more_notifications":"/notifications?offset=30&limit=30"}"""))
            val first = repository.noticePage(0)
            assertNotNull(first.next)
            assertEquals("/notifications?offset=0&limit=30", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"notifications":[{"id":2,"notification_type":25,"data":{}}],"total_rows_notifications":31,"load_more_notifications":"/notifications?offset=60&limit=30"}"""))
            val last = repository.noticePage(30, first.next)
            assertNull(last.next)
            assertTrue(NoticeGroup.LIKES.includes(last.notices.single()))
            assertEquals("/notifications?offset=30&limit=30", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
