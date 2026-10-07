package com.kudo.android.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class FailureKind { SIGN_IN, VERIFICATION, FORBIDDEN, RATE_LIMIT, NETWORK, SERVER }
class ApiFailure(val kind: FailureKind, override val message: String, val retryAfter: Int? = null) : IOException(message)

interface SessionCookies {
    fun get(url: String): String
    fun set(url: String, cookie: String)
}

class DiscourseClient(
    private val cookies: SessionCookies,
    val baseUrl: HttpUrl = "https://linux.do/".toHttpUrl(),
    private val userAgent: () -> String = { "Kudo/0.1 Android" },
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
) {
    private var csrf: String? = null
    private val csrfMutex = Mutex()
    fun resetSession() { csrf = null }

    fun resolve(path: String): HttpUrl {
        val url = baseUrl.resolve(path) ?: throw IllegalArgumentException("无效的社区地址")
        require(url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port && url.username.isEmpty() && url.password.isEmpty()) { "拒绝向社区以外的地址发送会话" }
        return url
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject = request("GET", path, query)
    suspend fun write(method: String, path: String, fields: Map<String, String> = emptyMap(), body: RequestBody? = null): JSONObject {
        val token = csrfMutex.withLock {
            csrf ?: get("/session/csrf").optString("csrf").takeIf { it.isNotBlank() && it != "null" }
                ?.also { csrf = it } ?: throw ApiFailure(FailureKind.SIGN_IN, "登录状态已失效，请重新登录")
        }
        return request(method, path, body = body ?: FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build(), token = token)
    }

    private suspend fun request(method: String, path: String, query: Map<String, String> = emptyMap(), body: RequestBody? = null, token: String? = null): JSONObject {
        val url = resolve(path).newBuilder().apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).method(method, body)
            .header("Accept", "application/json").header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", userAgent()).header("Referer", baseUrl.toString())
            .apply {
                cookies.get(url.toString()).takeIf(String::isNotBlank)?.let { header("Cookie", it) }
                token?.let { header("X-CSRF-Token", it); header("Origin", baseUrl.toString().trimEnd('/')) }
            }.build()
        val response = try { http.newCall(request).await() } catch (e: IOException) {
            throw ApiFailure(FailureKind.NETWORK, if (method == "GET") "网络连接失败，请检查网络后重试" else "请求结果尚未确认，请刷新确认后再操作，以免重复提交")
        }
        response.use {
            it.headers("Set-Cookie").forEach { cookie -> cookies.set(url.toString(), cookie) }
            val text = it.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            val reason = json?.optJSONArray("errors")?.strings()?.joinToString("\n")?.take(500)
                ?: json?.text("error")?.takeIf(String::isNotBlank)?.take(500)
            when {
                it.code == 401 -> throw ApiFailure(FailureKind.SIGN_IN, "请先登录 Linux.do")
                it.code == 429 -> throw ApiFailure(FailureKind.RATE_LIMIT, reason ?: "请求较频繁，请稍后再试", it.header("Retry-After")?.toIntOrNull())
                it.header("cf-mitigated") == "challenge" || (it.code == 403 && json == null) -> throw ApiFailure(FailureKind.VERIFICATION, "需要完成 Linux.do 的网页验证")
                it.code == 403 -> { csrf = null; throw ApiFailure(FailureKind.FORBIDDEN, reason ?: "当前账号没有权限，或登录状态需要刷新") }
                !it.isSuccessful -> throw ApiFailure(FailureKind.SERVER, reason ?: "社区暂时无法处理请求（${it.code}）")
                it.code == 204 || text.isBlank() -> return JSONObject()
                json == null -> throw ApiFailure(FailureKind.VERIFICATION, "社区返回了网页，请完成验证后重试")
                json.optBoolean("success", true).not() -> throw ApiFailure(FailureKind.SERVER, reason ?: "操作未成功，请稍后重试")
                else -> return json
            }
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
    })
}
