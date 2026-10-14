package com.kudo.android.network.limits

import okhttp3.OkHttpClient

/**
 * 请求限流能力的组合入口。
 *
 * 把主动限流（[RequestRateLimiter]）与 429 自动重放（[RateLimitRecovery]）打包，
 * 供各 OkHttp 客户端统一安装，避免每个客户端各自 new 一份导致 host 窗口不共享。
 */
class RequestLimits(
    val settings: RateLimitSettings = RateLimitSettings(),
) {
    val limiter = RequestRateLimiter(settings)
    val recovery = RateLimitRecovery()

    /** 在业务拦截器之后安装限流与恢复拦截器。 */
    fun install(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder.addInterceptor(limiter).addInterceptor(recovery)
}
