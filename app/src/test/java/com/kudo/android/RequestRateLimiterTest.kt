package com.kudo.android

import com.kudo.android.network.limits.RateLimitSettings
import com.kudo.android.network.limits.RequestRateLimiter
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RequestRateLimiterTest {
    private lateinit var server: MockWebServer

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun close() { server.shutdown() }

    private fun client(settings: RateLimitSettings): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(RequestRateLimiter(settings)).build()

    @Test fun enforcesWindowLimitByDelayingExcessRequests() {
        repeat(4) { server.enqueue(MockResponse().setBody("{}")) }
        val settings = RateLimitSettings(maxConcurrent = 1, maxPerWindow = 2, windowSeconds = 1)
        val http = client(settings)
        val start = System.nanoTime()
        repeat(4) { index ->
            http.newCall(Request.Builder().url(server.url("/r$index")).build()).execute().use { it.body?.string() }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(4, server.requestCount)
        assertTrue("expected window throttling, elapsed=${elapsedMs}ms", elapsedMs >= 1000)
    }

    @Test fun sharesWindowAcrossClientInstancesForSameHost() {
        repeat(3) { server.enqueue(MockResponse().setBody("{}")) }
        val settings = RateLimitSettings(maxConcurrent = 1, maxPerWindow = 2, windowSeconds = 1)
        val limiter = RequestRateLimiter(settings)
        val a = OkHttpClient.Builder().addInterceptor(limiter).build()
        val b = OkHttpClient.Builder().addInterceptor(limiter).build()
        val start = System.nanoTime()
        a.newCall(Request.Builder().url(server.url("/a")).build()).execute().close()
        b.newCall(Request.Builder().url(server.url("/b")).build()).execute().close()
        b.newCall(Request.Builder().url(server.url("/c")).build()).execute().close()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(3, server.requestCount)
        assertTrue("shared window should throttle third request, elapsed=${elapsedMs}ms", elapsedMs >= 1000)
    }

    @Test fun cancelledQueuedRequestDoesNotBlockOthers() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val settings = RateLimitSettings(maxConcurrent = 1, maxPerWindow = 1, windowSeconds = 5)
        val http = client(settings)
        val completed = AtomicInteger()
        val latch = CountDownLatch(1)
        val first = http.newCall(Request.Builder().url(server.url("/first")).build())
        first.execute().close()
        val queued = http.newCall(Request.Builder().url(server.url("/queued")).build())
        val thread = Thread { runCatching { queued.execute().close() }.also { completed.incrementAndGet(); latch.countDown() } }
        thread.start()
        Thread.sleep(150)
        queued.cancel()
        thread.join(3000)
        assertEquals(1, completed.get())
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
    }
}
