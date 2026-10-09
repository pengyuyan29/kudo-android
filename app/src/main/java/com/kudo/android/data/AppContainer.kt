package com.kudo.android.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
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
    val cookies = AndroidSessionCookies()
    val userAgent: String = WebSettings.getDefaultUserAgent(context.applicationContext)
    val client = DiscourseClient(cookies, userAgent = { userAgent })
    val repository = CommunityRepository(client)
    val connect = ConnectRepository(cookies, userAgent)
    val imageHttpClient = OkHttpClient.Builder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val builder = request.newBuilder().removeHeader("Cookie")
        if (request.url.isHttps && request.url.host == "linux.do") {
            builder.header("Cookie", cookies.get(request.url.toString())).header("User-Agent", userAgent)
        }
        chain.proceed(builder.build())
    }.build()
}
