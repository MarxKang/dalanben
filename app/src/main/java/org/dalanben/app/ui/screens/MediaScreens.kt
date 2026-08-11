package org.dalanben.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage

/** 图片查看器: 双指缩放 + 拖动 */
@Composable
fun ImageViewerScreen(navController: NavController, url: String) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                    if (scale <= 1f) { offsetX = 0f; offsetY = 0f }
                }
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY
            ),
            contentScale = ContentScale.Fit
        )
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 4.dp)
        ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White) }
    }
}

/** 视频播放器: Media3 ExoPlayer, 支持站内断点续播 */
@Composable
fun VideoPlayerScreen(navController: NavController, url: String, cover: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    // 断点续播: 进入时恢复上次位置
    LaunchedEffect(player, url) {
        val p = org.dalanben.app.data.SessionManager.getVideoProgress(context, url)
        if (p > 0) player.seekTo(p)
    }
    // 播放完成清除记录; 离开时保存当前位置
    DisposableEffect(player, url) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    org.dalanben.app.data.SessionManager.clearVideoProgress(context, url)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            val pos = player.currentPosition
            if (pos > 3000) org.dalanben.app.data.SessionManager.saveVideoProgress(context, url, pos)
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 4.dp)
        ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White) }
    }
}
