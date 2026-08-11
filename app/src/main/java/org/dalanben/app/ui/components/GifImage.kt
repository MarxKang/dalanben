package org.dalanben.app.ui.components

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL
import java.nio.ByteBuffer

/**
 * 跨版本 GIF 播放组件（minSdk 26 也可用）。
 * 不走 Coil：Coil 的 AsyncImage 在 Compose 下对 GIF 动画经常不触发播放，
 * 这里直接用 Android 原生解码器播放，保证动态星空云验证码真正动起来。
 */
@Composable
fun GifImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val drawableState = remember(url) { mutableStateOf<Drawable?>(null) }
    val aspectState = remember(url) { mutableStateOf<Float?>(null) }
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        launch(Dispatchers.IO) {
            try {
                val bytes = URL(url).readBytes()
                val d = decodeGif(bytes)
                aspectState.value = if (d.intrinsicHeight > 0)
                    d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat() else null
                drawableState.value = d
            } catch (_: Exception) {
                // 加载失败静默忽略，保留上一帧或空白
            }
        }
    }
    // 已知图片宽高比时按宽度铺满并自动算出高度（验证码原图很小，必须放大）；
    // 未知时给一个最小高度占位，避免加载瞬间高度塌成 0。
    val mod = if (aspectState.value != null && aspectState.value!! > 0f)
        modifier.aspectRatio(aspectState.value!!)
    else
        modifier.heightIn(min = 60.dp)
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription?.let { this.contentDescription = it }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // AnimatedImageDrawable 必须在挂到可见 ImageView 之后再 start()，
                // 否则动画线程不会真正驱动帧，表现为静止不动。
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        (getDrawable() as? AnimatedImageDrawable)?.start()
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        (getDrawable() as? AnimatedImageDrawable)?.stop()
                    }
                })
            }
        },
        update = { iv ->
            iv.setImageDrawable(drawableState.value)
            // 同样地，drawable 替换后立即补一次 start()，保证每次换验证码都重新循环
            (drawableState.value as? AnimatedImageDrawable)?.start()
        },
        modifier = mod
    )
}

private fun decodeGif(bytes: ByteArray): Drawable {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeImageDecoder(bytes)
    } else {
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("无法解码 GIF")
        GifMovieDrawable(movie)
    }
}

@TargetApi(Build.VERSION_CODES.P)
private fun decodeImageDecoder(bytes: ByteArray): Drawable {
    val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
    val d = ImageDecoder.decodeDrawable(src)
    if (d is AnimatedImageDrawable) {
        // 强制无限循环：第三方验证码 GIF 自带的 loop 元数据可能是 loop=1 / 不循环，
        // 这里用 REPEAT_INFINITE 覆盖，保证星空云验证码持续动起来。
        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        // start() 延后到挂到 ImageView 之后执行（见 factory 的 attach 监听与 update），此处不提前 start。
    }
    return d
}

/** 基于 android.graphics.Movie 的 GIF 动画 Drawable（API 26/27 回退方案） */
private class GifMovieDrawable(private val movie: Movie) : Drawable() {
    private var startTime = 0L

    override fun draw(canvas: Canvas) {
        if (startTime == 0L) startTime = SystemClock.uptimeMillis()
        val dur = movie.duration()
        val t = if (dur == 0) 0 else ((SystemClock.uptimeMillis() - startTime) % dur).toInt()
        movie.setTime(t)
        movie.draw(canvas, 0f, 0f)
        invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int = movie.width()
    override fun getIntrinsicHeight(): Int = movie.height()
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
