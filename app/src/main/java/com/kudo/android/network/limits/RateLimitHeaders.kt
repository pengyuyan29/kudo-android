package com.kudo.android.network.limits

import okhttp3.Headers
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 从 429/503 响应里解析"还要等多久"。
 *
 * linux.do(Discourse) 会用多种方式表达这个信息：
 * - 标准 `Retry-After`（秒数或 HTTP 日期）
 * - `x-ratelimit-reset` 等命名变体（秒数或 Unix 时间戳）
 * - 响应体 `extras.wait_seconds` / `extras.time_left`
 * - 错误文案里的中文"请等待 N 秒"或英文 "Please wait N seconds"
 *
 * DiscourseClient 与 DiscourseMessageBus 共用同一份口径，避免两套解析结果不一致。
 */
internal object RateLimitHeaders {

    fun from(response: Response): Int? {
        headerSeconds(response.headers)?.let { return it }
        return runCatching { response.peekBody(64 * 1024).string() }.getOrNull()?.let(::fromBodyText)
    }

    /** 仅从响应头解析，供只拿到 header 的调用方（如 MessageBus 的流式读取）复用。 */
    fun headerSeconds(headers: Headers): Int? {
        headers["Retry-After"]?.let { value ->
            value.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
            runCatching {
                val delta = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().epochSecond - java.time.Instant.now().epochSecond
                if (delta > 0) return delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
        val reset = listOf("x-ratelimit-reset", "ratelimit-reset", "x-rate-limit-reset")
            .firstNotNullOfOrNull { headers[it] }
        val resetSeconds = reset?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val nowSeconds = java.time.Instant.now().epochSecond
        val delta = if (resetSeconds > 1_000_000_000L) resetSeconds - nowSeconds else resetSeconds
        return delta.takeIf { it > 0 }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
    }

    fun fromBodyText(text: String): Int? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return parseWaitSecondsFromText(text)
        json.optJSONObject("extras")?.let { extras ->
            val raw = extras.opt("wait_seconds") ?: extras.opt("time_left")
            raw?.toString()?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        json.optString("error").takeIf { it.isNotBlank() }?.let { parseWaitSecondsFromText(it)?.let { seconds -> return seconds } }
        when (val errors = json.opt("errors")) {
            is JSONArray -> for (index in 0 until errors.length()) {
                parseWaitSecondsFromText(errors.optString(index))?.let { return it }
            }
            is String -> parseWaitSecondsFromText(errors)?.let { return it }
        }
        return null
    }

    private fun parseWaitSecondsFromText(message: String): Int? {
        Regex("请等待\\s*([0-9]+)\\s*(天|小时|分钟|秒)").find(message)?.let { match ->
            val value = match.groupValues[1].toIntOrNull() ?: return@let
            return secondsFromUnit(value, match.groupValues[2])
        }
        Regex("Please wait\\s+(\\d+)\\s+(second|seconds|minute|minutes|hour|hours|day|days)", RegexOption.IGNORE_CASE)
            .find(message)?.let { match ->
                val value = match.groupValues[1].toIntOrNull() ?: return@let
                return secondsFromUnit(value, match.groupValues[2].lowercase())
            }
        return null
    }

    private fun secondsFromUnit(value: Int, unit: String): Int = when {
        unit.contains("天") || unit.startsWith("day") -> value * 86400
        unit.contains("小时") || unit.startsWith("hour") -> value * 3600
        unit.contains("分钟") || unit.startsWith("minute") -> value * 60
        else -> value
    }
}
