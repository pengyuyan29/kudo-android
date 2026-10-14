package com.kudo.android

import com.kudo.android.network.limits.RateLimitHeaders
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test

class RateLimitHeadersTest {
    @Test fun parsesRetryAfterSeconds() {
        assertEquals(30, RateLimitHeaders.headerSeconds(Headers.headersOf("Retry-After", "30")))
    }

    @Test fun parsesRetryAfterHttpDate() {
        val future = java.time.ZonedDateTime.now().plusSeconds(120)
            .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        val seconds = RateLimitHeaders.headerSeconds(Headers.headersOf("Retry-After", future))
        assertNotNull(seconds)
        assertTrue(seconds!! in 110..121)
    }

    @Test fun ignoresNonPositiveAndInvalidRetryAfter() {
        assertNull(RateLimitHeaders.headerSeconds(Headers.headersOf("Retry-After", "0")))
        assertNull(RateLimitHeaders.headerSeconds(Headers.headersOf("Retry-After", "abc")))
    }

    @Test fun parsesRateLimitResetVariants() {
        val now = java.time.Instant.now().epochSecond
        assertEquals(45, RateLimitHeaders.headerSeconds(Headers.headersOf("x-ratelimit-reset", (now + 45).toString())))
        assertEquals(20, RateLimitHeaders.headerSeconds(Headers.headersOf("ratelimit-reset", "20")))
    }

    @Test fun parsesWaitSecondsFromBody() {
        assertEquals(12, RateLimitHeaders.fromBodyText("""{"extras":{"wait_seconds":12}}"""))
        assertEquals(8, RateLimitHeaders.fromBodyText("""{"extras":{"time_left":8}}"""))
    }

    @Test fun parsesChineseAndEnglishWaitText() {
        assertEquals(30, RateLimitHeaders.fromBodyText("""{"error":"请等待 30 秒后再试"}"""))
        assertEquals(120, RateLimitHeaders.fromBodyText("""{"errors":["请等待 2 分钟"]}"""))
        assertEquals(3600, RateLimitHeaders.fromBodyText("""{"error":"Please wait 1 hour"}"""))
        assertEquals(86400, RateLimitHeaders.fromBodyText("""{"error":"请等待 1 天"}"""))
    }

    @Test fun returnsNullWhenNoWaitInformation() {
        assertNull(RateLimitHeaders.fromBodyText("""{"error":"无权限"}"""))
        assertNull(RateLimitHeaders.fromBodyText("not json at all"))
    }
}
