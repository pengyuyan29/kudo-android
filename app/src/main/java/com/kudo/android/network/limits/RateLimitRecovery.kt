package com.kudo.android.network.limits

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 429 自动重放拦截器。
 *
 * - 解析到等待时长且 ≤ [maxWaitSeconds] 时，延迟后自动重放一次；
 * - 超过上限或解析不到时长时，原样返回 429 响应，交业务层提示用户；
 * - 只对幂等方法（GET/HEAD/OPTIONS）自动重放，写操作绝不重放，避免重复发帖；
 * - 用尝试预算 [maxAttempts] 防环，由结构保证不会无限重试。
 */
class RateLimitRecovery(
    private val maxWaitSeconds: Int = DEFAULT_MAX_WAIT_SECONDS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            val response = chain.proceed(chain.request())
            if (response.code != 429) return response
            val method = chain.request().method.uppercase()
            if (method !in IDEMPOTENT_METHODS) return response
            if (attempt >= maxAttempts - 1) return response
            val waitSeconds = RateLimitHeaders.from(response) ?: return response
            if (waitSeconds <= 0 || waitSeconds > maxWaitSeconds) return response
            response.close()
            attempt++
            try {
                Thread.sleep(waitSeconds * 1000L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("请求在限流等待中被中断", e)
            }
            if (chain.call().isCanceled()) throw IOException("请求在限流等待中被取消")
        }
    }

    companion object {
        const val DEFAULT_MAX_WAIT_SECONDS = 30
        const val DEFAULT_MAX_ATTEMPTS = 4
        private val IDEMPOTENT_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }
}
