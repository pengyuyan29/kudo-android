package com.kudo.android.network.limits

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 请求限流的全局配置。
 *
 * [RequestRateLimiter] 在每次请求时动态读取这些值，用户修改后立即生效。
 * 默认值对齐 FluxDO：并发 3、3 秒窗口内最多 6 个请求。
 */
class RateLimitSettings(
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT,
    maxPerWindow: Int = DEFAULT_MAX_PER_WINDOW,
    windowSeconds: Int = DEFAULT_WINDOW_SECONDS,
) {
    data class Values(val maxConcurrent: Int, val maxPerWindow: Int, val windowSeconds: Int)

    private val _values = MutableStateFlow(
        Values(
            maxConcurrent.coerceIn(1, 16),
            maxPerWindow.coerceIn(1, 60),
            windowSeconds.coerceIn(1, 60),
        )
    )
    val values = _values.asStateFlow()

    val maxConcurrent: Int get() = _values.value.maxConcurrent
    val maxPerWindow: Int get() = _values.value.maxPerWindow
    val windowSeconds: Int get() = _values.value.windowSeconds

    fun update(maxConcurrent: Int = this.maxConcurrent, maxPerWindow: Int = this.maxPerWindow, windowSeconds: Int = this.windowSeconds) {
        _values.value = Values(
            maxConcurrent.coerceIn(1, 16),
            maxPerWindow.coerceIn(1, 60),
            windowSeconds.coerceIn(1, 60),
        )
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENT = 3
        const val DEFAULT_MAX_PER_WINDOW = 6
        const val DEFAULT_WINDOW_SECONDS = 3
    }
}
