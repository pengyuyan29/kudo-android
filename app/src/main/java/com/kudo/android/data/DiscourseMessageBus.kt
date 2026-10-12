package com.kudo.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
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

fun interface TopicUpdates {
    fun observeTopic(user: Member?, topicId: Int): Flow<Unit>
}

class DiscourseMessageBus(
    private val client: DiscourseClient,
    private val endpoint: HttpUrl = "https://ping.ldstatic.com/".toHttpUrl(),
    private val userAgent: String = "Kudo/0.1 Android",
    route: (OkHttpClient) -> Call.Factory = { it },
) : FeedUpdates, NotificationUpdates, TopicUpdates {
    private companion object {
        const val maxConsecutiveVerificationFailures = 2
    }
    // Intentionally independent of the main-site cookie jar and interceptors.
    private val http = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).build()
    private val calls = route(http)

    override fun observe(user: Member?): Flow<Unit> = flow {
        val cursors = MessageBusCursors(user?.id)
        poll(user, cursors.values).collect { batch ->
            val changed = cursors.accept(batch.messages)
            if (changed || batch.reconnect) emit(Unit)
        }
    }

    override fun notifications(user: Member): Flow<NotificationBatch> = flow {
        val cursors = NotificationCursors(user)
        poll(user, cursors.values).collect { batch ->
            emit(cursors.accept(batch.messages, batch.initial, batch.reconnect))
        }
    }

    override fun observeTopic(user: Member?, topicId: Int): Flow<Unit> = flow {
        require(topicId > 0)
        val cursors = TopicMessageBusCursors(topicId)
        poll(user, cursors.values).collect { batch ->
            val changed = cursors.accept(batch.messages)
            if (changed || batch.reconnect) emit(Unit)
        }
    }

    private data class PollBatch(val messages: JSONArray, val initial: Boolean, val reconnect: Boolean)
    private fun poll(user: Member?, cursors: Map<String, Long>): Flow<PollBatch> = flow {
        val id = UUID.randomUUID().toString().replace("-", "")
        var key: String? = null
        var sequence = 0L
        var failures = 0
        var first = true
        var reconnect = true
        var verificationFailures = 0
        while (true) {
            try {
                if (user != null && key == null) key = client.sharedSessionKey()
                val url = endpoint.newBuilder().addPathSegments("message-bus/$id/poll")
                    .apply { if (first) addQueryParameter("dlp", "t") }.build()
                val body = FormBody.Builder().apply {
                    cursors.forEach { (channel, cursor) -> add(channel, cursor.toString()) }
                    add("__seq", (++sequence).toString())
                }.build()
                val request = Request.Builder().url(url).post(body)
                    .header("Dont-Chunk", "true").header("Accept", "application/json")
                    .header("Origin", client.baseUrl.toString().trimEnd('/'))
                    .header("X-SILENCE-LOGGER", "true").header("User-Agent", userAgent)
                    .apply { key?.let { header("X-Shared-Session-Key", it) } }.build()
                emit(PollBatch(calls.newCall(request).poll(), first, reconnect))
                first = false
                reconnect = false
                failures = 0
                verificationFailures = 0
                // A misconfigured server returning immediate empty bodies must not create a hot loop.
                delay(1000)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (e is ApiFailure && e.kind == FailureKind.VERIFICATION) {
                    verificationFailures++
                    if (verificationFailures >= maxConsecutiveVerificationFailures) throw e
                }
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

internal class TopicMessageBusCursors(topicId: Int) {
    private val channel = "/topic/$topicId"
    val values = linkedMapOf(channel to -1L)

    fun accept(messages: JSONArray): Boolean {
        var changed = false
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            when (message.optString("channel")) {
                "/__status" -> {
                    val head = message.optJSONObject("data")?.optLong(channel, -1) ?: -1
                    if (head >= 0 && head != values[channel]) {
                        values[channel] = head
                        changed = true
                    }
                }
                channel -> {
                    val cursor = message.optLong("message_id", -1)
                    if (cursor <= values.getValue(channel)) continue
                    values[channel] = cursor
                    val data = message.optJSONObject("data") ?: continue
                    if (data.optString("type") == "created" ||
                        (data.optBoolean("reload_topic") && data.optBoolean("refresh_stream"))) changed = true
                }
            }
        }
        return changed
    }
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
                    val body = it.body?.string().orEmpty()
                    when {
                        isCloudflareChallenge(it, body) -> throw ApiFailure(FailureKind.VERIFICATION, CloudflareVerificationMessage)
                        it.code == 429 -> throw ApiFailure(FailureKind.RATE_LIMIT, "推送限流", retryAfterSeconds(it.header("Retry-After")))
                        it.code == 401 || it.code == 403 -> throw ApiFailure(FailureKind.SIGN_IN, "推送会话失效")
                        !it.isSuccessful -> throw IOException("推送请求失败：${it.code}")
                        else -> JSONArray(body)
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
