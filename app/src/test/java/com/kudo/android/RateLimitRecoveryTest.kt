package com.kudo.android

import com.kudo.android.network.limits.RateLimitRecovery
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RateLimitRecoveryTest {
    private lateinit var server: MockWebServer

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun close() { server.shutdown() }

    private fun client(maxWait: Int = 30, maxAttempts: Int = 4): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(RateLimitRecovery(maxWait, maxAttempts)).build()

    @Test fun replaysIdempotentGetAfterRateLimit() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("""{"errors":["慢一点"]}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        client().newCall(Request.Builder().url(server.url("/latest.json")).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("""{"ok":true}""", it.body?.string())
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun doesNotReplayWriteRequests() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("{}"))
        val response = client().newCall(
            Request.Builder().url(server.url("/posts.json")).post("raw=hi".toRequestBody()).build()
        ).execute()
        response.use { assertEquals(429, it.code) }
        assertEquals(1, server.requestCount)
    }

    @Test fun doesNotWaitWhenRetryAfterExceedsLimit() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120").setBody("{}"))
        val start = System.nanoTime()
        client(maxWait = 30).newCall(Request.Builder().url(server.url("/latest.json")).build()).execute().use {
            assertEquals(429, it.code)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(1, server.requestCount)
        assertTrue("should not wait, elapsed=${elapsedMs}ms", elapsedMs < 1000)
    }

    @Test fun stopsReplayingAfterAttemptBudget() {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody("{}")) }
        client(maxAttempts = 2).newCall(Request.Builder().url(server.url("/latest.json")).build()).execute().use {
            assertEquals(429, it.code)
        }
        assertEquals(2, server.requestCount)
    }
}
