package com.kudo.android.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.kudo.android.network.limits.RequestLimits
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AndroidSessionCookies : SessionCookies {
    private val manager get() = CookieManager.getInstance()
    override fun get(url: String): String = manager.getCookie(url).orEmpty()
    override fun set(url: String, cookie: String) { manager.setCookie(url, cookie); manager.flush() }
    suspend fun clear() = suspendCancellableCoroutine { continuation ->
        manager.removeAllCookies { manager.flush(); if (continuation.isActive) continuation.resume(Unit) }
    }
}

class AppContainer(context: Context) {
    val settings = AppSettings(context.applicationContext)
    val network = com.kudo.android.network.DohNetwork(context.applicationContext, DohSettings(context.applicationContext))
    val notifications = com.kudo.android.notifications.LocalNotifications(context.applicationContext)
    val cookies = AndroidSessionCookies()
    val userAgent: String = WebSettings.getDefaultUserAgent(context.applicationContext)
    val limits = RequestLimits()
    val client = DiscourseClient(cookies, userAgent = { userAgent }, route = network::client,
        http = limits.install(OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)).build())
    val repository = CommunityRepository(client)
    val connect = ConnectRepository(cookies, userAgent, route = network::client,
        http = limits.install(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(35, TimeUnit.SECONDS)).build())
    val imageHttpClient: okhttp3.Call.Factory = network.client(OkHttpClient.Builder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder().removeHeader("Cookie")
        if (request.url.isHttps && request.url.host == "linux.do") {
            builder.header("Cookie", cookies.get(request.url.toString())).header("User-Agent", userAgent)
        }
        chain.proceed(builder.build())
    }.build())
}
