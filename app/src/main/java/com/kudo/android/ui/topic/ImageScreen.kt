package com.kudo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ImageScreen(url: String, back: () -> Unit, open: () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color(0xFF101010))) {
        AsyncImage(url, "图片，双指缩放", Modifier.fillMaxSize().pointerInput(url) {
            detectTransformGestures { _, pan, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 5f); offset = if (zoom == 1f) Offset.Zero else offset + pan }
        }.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = back) { Icon(Icons.Rounded.Close, "关闭图片", tint = Color.White) }
            IconButton(onClick = open) { Icon(Icons.Rounded.OpenInBrowser, "在浏览器打开或保存", tint = Color.White) }
        }
    }
}
