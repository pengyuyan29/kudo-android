package com.kudo.android

import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DiscourseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DiscourseClient
    @Before fun setup() { server = MockWebServer(); server.start(); client = DiscourseClient(NoCookies, server.url("/")) }
    @After fun close() { server.shutdown() }

    @Test fun serverPaginationCannotSendSessionToAnotherOrigin() {
        listOf("https://attacker.example/latest.json", "//attacker.example/latest.json", "http://user:secret@localhost:${server.port}/").forEach {
            assertThrows(IllegalArgumentException::class.java) { client.resolve(it) }
        }
        assertEquals(server.url("/latest.json?page=2"), client.resolve("/latest.json?page=2"))
    }
    @Test fun writeFetchesCsrfAndIsNotRetriedAfterServerFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"csrf":"test-token"}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"errors":["稍后重试"]}"""))
        val failure = runCatching { client.write("POST", "/posts.json", mapOf("raw" to "测试内容")) }.exceptionOrNull()
        assertEquals(FailureKind.SERVER, (failure as ApiFailure).kind)
        assertEquals(2, server.requestCount)
        assertEquals("/session/csrf", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("test-token", post.getHeader("X-CSRF-Token"))
        assertEquals("POST", post.method)
        assertTrue(post.body.readUtf8().contains("raw="))
    }
    @Test fun challengeIsNotReportedAsEmptySuccessfulContent() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("cf-mitigated", "challenge").setBody("<html>Verify</html>"))
        val failure = runCatching { client.get("/latest.json") }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.VERIFICATION, failure.kind)
        assertTrue(failure.message.contains("Cloudflare"))
    }
    @Test fun htmlChallengeWithoutCloudflareHeaderIsReportedForManualVerification() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/a.js'></script></html>"))
        val failure = runCatching { client.get("/latest.json") }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.VERIFICATION, failure.kind)
        assertTrue(failure.message.contains("手动完成验证"))
    }
    @Test fun rateLimitPreservesRetryAfterAndDoesNotRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("""{"errors":["慢一点"]}"""))
        val failure = runCatching { client.get("/latest.json") }.exceptionOrNull() as ApiFailure
        assertEquals(FailureKind.RATE_LIMIT, failure.kind)
        assertEquals(30, failure.retryAfter)
        assertEquals(1, server.requestCount)
    }
    @Test fun unauthorizedRequiresLogin() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertEquals(FailureKind.SIGN_IN, (runCatching { client.get("/notifications") }.exceptionOrNull() as ApiFailure).kind)
    }
    @Test fun discourseNotLoggedIn403RequiresLoginInsteadOfRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"errors":["您需要登录才能执行此操作。"],"error_type":"not_logged_in"}"""))
        assertEquals(FailureKind.SIGN_IN, (runCatching { client.get("/search.json") }.exceptionOrNull() as ApiFailure).kind)
    }
    @Test fun redirectDoesNotForwardRequestAutomatically() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/"))
        assertTrue(runCatching { client.get("/latest.json") }.isFailure)
        assertEquals(1, server.requestCount)
    }
    @Test fun categoryCanonicalRedirectFollowsOnlySameOriginGet() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/c/develop/4/l/latest.json"))
        server.enqueue(MockResponse().setBody("""{"topic_list":{"topics":[{"id":42,"title":"分类话题"}]}}"""))
        val page = CommunityRepository(client).topics(categoryId = 4)
        assertEquals(42, page.topics.single().id)
        assertEquals("/c/4/l/latest.json", server.takeRequest().path)
        assertEquals("/c/develop/4/l/latest.json", server.takeRequest().path)
    }
    @Test fun writeRedirectIsNeverReplayed() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"csrf":"test-token"}"""))
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/posts.json"))
        assertTrue(runCatching { client.write("POST", "/posts", mapOf("raw" to "测试")) }.isFailure)
        assertEquals(2, server.requestCount)
    }
    @Test fun sameOriginRedirectLoopStopsAfterThreeHops() = runBlocking {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/loop")) }
        assertTrue(runCatching { client.get("/loop") }.isFailure)
        assertEquals(4, server.requestCount)
    }
}
