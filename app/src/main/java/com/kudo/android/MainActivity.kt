package com.kudo.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kudo.android.ui.KudoApp

class MainActivity : ComponentActivity() {
    private var incomingUrl by mutableStateOf<String?>(null)
    private var linkSequence by mutableStateOf(0L)
    private var incomingNotification by mutableStateOf<com.kudo.android.data.NotificationTarget?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as KudoApplication).container
        incomingUrl = intent?.dataString.takeUnless { savedInstanceState?.getBoolean("link_consumed") == true }
        incomingNotification = app.notifications.fromIntent(intent).takeUnless { savedInstanceState?.getBoolean("notification_consumed") == true }
        if (incomingNotification != null) incomingUrl = null
        setContent { KudoApp(app, incomingUrl, linkSequence, consumeLink = { incomingUrl = null },
            incomingNotification = incomingNotification, consumeNotification = { incomingNotification = null }) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkSequence++
        incomingUrl = intent.dataString
        val app = (application as KudoApplication).container
        incomingNotification = app.notifications.fromIntent(intent)
        if (incomingNotification != null) incomingUrl = null
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("link_consumed", incomingUrl == null)
        outState.putBoolean("notification_consumed", incomingNotification == null)
        super.onSaveInstanceState(outState)
    }
}
