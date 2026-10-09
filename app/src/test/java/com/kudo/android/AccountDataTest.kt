package com.kudo.android

import com.kudo.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

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
}
