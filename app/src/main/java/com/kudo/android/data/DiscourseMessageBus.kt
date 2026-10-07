package com.kudo.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

fun interface FeedUpdates {
    // Each collection owns its credentials and cursors; cancellation closes the active poll.
    fun observe(user: Member?): Flow<Unit>
}

class DiscourseMessageBus(
    private val client: DiscourseClient,
    private val endpoint: HttpUrl = "https://ping.ldstatic.com/".toHttpUrl(),
    private val userAgent: String = "Kudo/0.1 Android",
) : FeedUpdates {
    // Intentionally independent of the main-site cookie jar and interceptors.
    private val http = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).build()

    override fun observe(user: Member?): Flow<Unit> = flow {
        val cursors = MessageBusCursors(user?.id)
        val id = UUID.randomUUID().toString().replace("-", "")
        var key: String? = null
        var sequence = 0L
        var failures = 0
        var first = true
        var reconnect = true
        while (true) {
            try {
                if (user != null && key == null) key = client.sharedSessionKey()
                val url = endpoint.newBuilder().addPathSegments("message-bus/$id/poll")
                    .apply { if (first) addQueryParameter("dlp", "t") }.build()
                val body = FormBody.Builder().apply {
                    cursors.values.forEach { (channel, cursor) -> add(channel, cursor.toString()) }
                    add("__seq", (++sequence).toString())
                }.build()
                val request = Request.Builder().url(url).post(body)
                    .header("Dont-Chunk", "true").header("Accept", "application/json")
                    .header("Origin", client.baseUrl.toString().trimEnd('/'))
                    .header("X-SILENCE-LOGGER", "true").header("User-Agent", userAgent)
                    .apply { key?.let { header("X-Shared-Session-Key", it) } }.build()
                val changed = cursors.accept(http.newCall(request).poll())
                if (changed || reconnect) emit(Unit)
                first = false
                reconnect = false
                failures = 0
                // A misconfigured server returning immediate empty bodies must not create a hot loop.
                delay(1000)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                reconnect = true
                if (e is ApiFailure && e.kind in setOf(FailureKind.SIGN_IN, FailureKind.FORBIDDEN)) key = null
                val retrySeconds = (e as? ApiFailure)?.retryAfter?.coerceAtLeast(1)
                    ?: ((1L shl failures.coerceAtMost(5)) * 2 + Random.nextLong(2)).coerceAtMost(60).toInt()
                failures++
                delay(retrySeconds * 1000L)
            }
        }
    }.flowOn(Dispatchers.IO)
}

internal class MessageBusCursors(userId: Int?) {
    val values = linkedMapOf("/latest" to -1L).apply {
        if (userId != null) {
            put("/new", -1L)
            put("/unread", -1L)
            put("/unread/$userId", -1L)
        }
    }

    fun accept(messages: JSONArray): Boolean {
        var changed = false
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            val channel = message.optString("channel")
            if (channel == "/__status") {
                val status = message.optJSONObject("data") ?: continue
                values.keys.toList().forEach { name ->
                    if (status.has(name)) {
                        val head = status.optLong(name, -1)
                        if (head >= 0 && head != values[name]) {
                            // Includes server restarts and expired backlogs: the list must catch up.
                            values[name] = head
                            changed = true
                        }
                    }
                }
            } else {
                val previous = values[channel] ?: continue
                val cursor = message.optLong("message_id", -1)
                if (cursor > previous) {
                    values[channel] = cursor
                    changed = true
                }
            }
        }
        return changed
    }
}

private suspend fun Call.poll(): JSONArray = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val messages = response.use {
                    when {
                        it.code == 429 -> throw ApiFailure(FailureKind.RATE_LIMIT, "推送限流", retryAfterSeconds(it.header("Retry-After")))
                        it.code == 401 || it.code == 403 -> throw ApiFailure(FailureKind.SIGN_IN, "推送会话失效")
                        !it.isSuccessful -> throw IOException("推送请求失败：${it.code}")
                        else -> JSONArray(it.body?.string().orEmpty())
                    }
                }
                if (continuation.isActive) continuation.resume(messages)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    })
}

internal fun retryAfterSeconds(value: String?): Int? = value?.toIntOrNull()?.coerceAtLeast(1)
    ?: runCatching {
        val time = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().epochSecond
        (time - java.time.Instant.now().epochSecond).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }.getOrNull()
