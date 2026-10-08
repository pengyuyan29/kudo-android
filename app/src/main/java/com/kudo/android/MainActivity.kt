package com.kudo.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.kudo.android.data.AppContainer
import com.kudo.android.media.communityAnimations
import com.kudo.android.ui.KudoApp

class MainActivity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = AppContainer(applicationContext)
        SingletonImageLoader.setSafe { context -> ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { app.imageHttpClient })) }.communityAnimations().build() }
        incomingUrl = intent?.dataString
        setContent { KudoApp(app, incomingUrl) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); incomingUrl = intent.dataString }
}
