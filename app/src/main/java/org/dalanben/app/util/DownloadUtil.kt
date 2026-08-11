package org.dalanben.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.dalanben.app.data.BASE_URL
import org.dalanben.app.data.Post
import org.dalanben.app.data.Session
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.DownloadUiState
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 保存作品到相册时使用的子目录（位于 DCIM 下，相册 App 可直接看到） */
private const val GALLERY_ALBUM = "Dalanben"

/** 公开媒体（图片等）下载客户端 */
private val dlClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

/** 带鉴权的视频下载客户端（服务端 ffmpeg 烧录可能耗时较久，放宽超时）。
 *  必须携带登录 token，否则服务端 /download_watermarked 会返回 401 JSON 导致保存失败。 */
private val wmClient = OkHttpClient.Builder()
    .addInterceptor(Interceptor { chain ->
        val orig = chain.request()
        val builder = orig.newBuilder()
        Session.token?.let { builder.header("Authorization", "Bearer $it") }
        chain.proceed(builder.build())
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

/** 当前活跃下载任务，用于支持「停止」打断 */
private var activeJob: Job? = null
private var activeCall: okhttp3.Call? = null
private var cancelled: Boolean = false

/** 取消当前下载/烧录：中断网络请求与协程 */
fun cancelActiveDownload() {
    cancelled = true
    try { activeCall?.cancel() } catch (_: Exception) {}
    try { activeJob?.cancel() } catch (_: Exception) {}
    activeCall = null
    activeJob = null
}

/**
 * 下载他人作品（图片 / 视频）并保存到手机相册。
 *
 *  1. 通过 OkHttp 流式下载并实时回调进度与预计剩余时间；
 *  2. 图片：在作品右下角叠加纯文字平台水印（第一行「大蓝本社区」蓝色字体 + 深色手动阴影，
 *     第二行「蓝本号：xxx」白色字体 + 深色手动阴影）；
 *  3. 视频：请求服务端 ffmpeg 烧录好的带水印视频（/api/post/download_watermarked），
 *     客户端仅负责下载与存相册——比移动端 Media3 烧录更可靠；
 *  4. 通过 MediaStore 写入 DCIM/Dalanben，直接进入系统相册（图库），而非 Download 目录；
 *  5. 全程支持「停止」打断（见 [cancelActiveDownload]）。
 *
 * @param post   目标作品（详情页已携带 author，可取到蓝本号 blueId）
 * @param appVm  用于驱动下载进度浮层与结果 Toast
 */
fun downloadPost(context: Context, post: Post, appVm: AppViewModel) {
    val isVideo = post.postType == "video"
    val urls = if (isVideo) post.mediaUrls.safeMedia().take(1) else post.mediaUrls.safeMedia().take(4)
    if (urls.isEmpty()) {
        appVm.showToast("该作品暂无可下载的媒体")
        return
    }
    // 下载/分享场景强制叠加平台水印，保证品牌露出（不再依赖 post.watermark 开关）
    val addWatermark = true
    val blueId = (post.author?.blueId ?: post.nickname ?: "大蓝本用户").ifBlank { "大蓝本用户" }

    // 协程作用域（沿用项目既有 GlobalScope.IO 用法）
    cancelled = false
    activeJob = GlobalScope.launch(Dispatchers.IO) {
        try {
            var okCount = 0
            for ((idx, url) in urls.withIndex()) {
                if (cancelled) break
                try {
                    if (isVideo) {
                        // 视频：服务端已烧录好水印，客户端只下载并存相册
                        val phaseText = "正在生成带水印视频 ${idx + 1}/${urls.size}（通常 10–30 秒）"
                        val prepStart = System.currentTimeMillis()
                        // 准备阶段：每秒刷新“已等待”时间（服务端不回传进度）
                        val ticker = this.launch {
                            while (!cancelled) {
                                delay(1000)
                                val el = ((System.currentTimeMillis() - prepStart) / 1000).toInt()
                                appVm.showDownloadProgress(-1, phaseText, null, el)
                            }
                        }
                        appVm.showDownloadProgress(-1, phaseText, null, 0)
                        val wmUrl = "$BASE_URL/api/post/download_watermarked?post_id=${post.id}"
                        val tmpV = File(context.cacheDir, "dl_${post.id}_${System.currentTimeMillis()}_$idx.mp4")
                        var lastPct = -2
                        val okV = downloadFileWithProgressAuth(wmUrl, tmpV) { pct, eta ->
                            if (pct != lastPct) {
                                lastPct = pct
                                appVm.showDownloadProgress(pct, "下载带水印视频 ${idx + 1}/${urls.size}", eta, 0)
                            }
                        }
                        ticker.cancel()
                        if (cancelled) break
                        if (okV && tmpV.exists() && tmpV.length() > 0) {
                            val name = "大蓝本_${post.id}_${System.currentTimeMillis()}.mp4"
                            if (saveVideoToGallery(context, tmpV, name) != null) okCount++
                        }
                        tmpV.delete()
                    } else {
                        val dl = fullUrl(url) ?: url
                        val tmp = File(context.cacheDir, "dl_${post.id}_${System.currentTimeMillis()}_$idx.jpg")
                        var lastPct = -2
                        downloadFileWithProgress(dl, tmp) { pct, eta ->
                            if (pct != lastPct) {
                                lastPct = pct
                                appVm.showDownloadProgress(pct, "下载图片 ${idx + 1}/${urls.size}", eta, 0)
                            }
                        }
                        if (cancelled) break
                        if (!tmp.exists() || tmp.length() == 0L) {
                            tmp.delete()
                            continue
                        }
                        val src = BitmapFactory.decodeFile(tmp.absolutePath)
                        val wmBmp = if (src != null && addWatermark && !isLikelyGif(tmp)) {
                            val badge = buildWatermarkBitmap(src.width, src.height, blueId)
                            addWatermarkToImage(src, badge)
                        } else {
                            null
                        }
                        val name = "大蓝本_${post.id}_${System.currentTimeMillis()}.jpg"
                        val bmpToSave = wmBmp ?: src
                        if (bmpToSave != null && saveImageToGallery(context, bmpToSave, name) != null) okCount++
                        src?.recycle()
                        wmBmp?.recycle()
                        tmp.delete()
                    }
                } catch (_: CancellationException) {
                    // 被「停止」打断，直接结束整个下载
                    return@launch
                } catch (_: Exception) {
                    if (cancelled) return@launch
                    // 单个文件失败不影响其余
                }
            }
            if (cancelled) {
                appVm.dismissDownload()
                appVm.showToast("已取消下载")
                return@launch
            }
            appVm.dismissDownload()
            appVm.showToast(
                if (okCount > 0) {
                    "已保存 $okCount 个作品到相册（含平台水印）"
                } else {
                    "保存失败，请重试"
                }
            )
        } catch (_: CancellationException) {
            appVm.dismissDownload()
            appVm.showToast("已取消下载")
        } catch (_: Exception) {
            appVm.dismissDownload()
            appVm.showToast("保存失败，请重试")
        }
    }
}

/** 流式下载（无鉴权，用于图片等公开媒体）并实时回调进度（0..100；无 content-length 时为 -1）与预计剩余秒数 */
private suspend fun downloadFileWithProgress(
    url: String,
    dest: File,
    onProgress: (Int, Int?) -> Unit,
) {
    val req = Request.Builder().url(url).build()
    val call = dlClient.newCall(req)
    activeCall = call
    try {
        val resp = call.execute()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        val body = resp.body ?: throw IOException("空响应")
        val total = body.contentLength()
        val startMs = System.currentTimeMillis()
        dest.outputStream().use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                var downloaded = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1
                    val eta = if (total > 0 && downloaded > 0) {
                        val el = System.currentTimeMillis() - startMs
                        if (el > 0) (((total - downloaded) / (downloaded.toDouble() / el)) / 1000).toInt() else null
                    } else null
                    onProgress(pct, eta)
                }
            }
        }
        onProgress(100, null)
    } finally {
        activeCall = null
    }
}

/** 流式下载（带鉴权，使用带长超时的客户端）并实时回调进度与预计剩余秒数；返回是否成功 */
private suspend fun downloadFileWithProgressAuth(
    url: String,
    dest: File,
    onProgress: (Int, Int?) -> Unit,
): Boolean {
    val req = Request.Builder().url(url).get().build()
    val call = wmClient.newCall(req)
    activeCall = call
    val resp = try {
        call.execute()
    } catch (_: Exception) {
        activeCall = null
        return false
    }
    try {
        val ct = resp.header("Content-Type") ?: ""
        if (!resp.isSuccessful || !ct.contains("video", ignoreCase = true)) {
            resp.close()
            return false
        }
        val body = resp.body ?: return false
        val total = body.contentLength()
        val startMs = System.currentTimeMillis()
        return try {
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else -1
                        val eta = if (total > 0 && downloaded > 0) {
                            val el = System.currentTimeMillis() - startMs
                            if (el > 0) (((total - downloaded) / (downloaded.toDouble() / el)) / 1000).toInt() else null
                        } else null
                        onProgress(pct, eta)
                    }
                }
            }
            onProgress(100, null)
            dest.exists() && dest.length() > 0
        } catch (_: Exception) {
            false
        } finally {
            // body 已关闭
        }
    } finally {
        try { resp.close() } catch (_: Exception) {}
        activeCall = null
    }
}

/**
 * 构建纯文字水印 Bitmap（透明底，无 logo、无底色方框）：
 *   第一行「大蓝本社区」——蓝色字体 + 深色手动阴影；
 *   第二行「蓝本号：xxx」——白色字体 + 深色手动阴影。
 * 采用「先画偏移深色副本、再画彩色文字」的方式实现阴影，避免 setShadowLayer 在部分机型
 * 上产生异常渲染（如大面积扇形伪影）。字号按目标媒体较短边自适应。
 */
private fun buildWatermarkBitmap(
    targetW: Int,
    targetH: Int,
    blueId: String,
): Bitmap {
    val ref = min(targetW, targetH).coerceAtLeast(1).toFloat()
    val textSize = (ref * 0.035f).coerceAtLeast(12f)
    val line1 = "大蓝本社区"
    val line2 = "蓝本号：$blueId"
    val lineH = textSize
    val gap = textSize * 0.28f
    val shadowDx = textSize * 0.06f
    val shadowDy = textSize * 0.08f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        textAlign = Paint.Align.LEFT
        color = Color.parseColor("#CC000000") // 半透明黑，作为阴影
    }
    val bluePaint = Paint(shadowPaint).apply { color = Color.parseColor("#4A6CF7") }
    val whitePaint = Paint(shadowPaint).apply { color = Color.WHITE }

    val w1 = bluePaint.measureText(line1)
    val w2 = whitePaint.measureText(line2)
    val textW = maxOf(w1, w2)
    val contentH = lineH * 2 + gap
    val paddingX = max(ref * 0.02f, 6f)
    val paddingY = max(ref * 0.015f, 5f)
    val badgeW = (textW + paddingX * 2f).roundToInt().coerceAtLeast(1)
    val badgeH = (contentH + paddingY * 2f + shadowDy + textSize * 0.25f).roundToInt().coerceAtLeast(1)

    // 透明底，仅绘制文字 + 手动阴影
    val bmp = Bitmap.createBitmap(badgeW, badgeH, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val baseY = paddingY + lineH // 第一行基线
    // 第一行：阴影 + 蓝字
    c.drawText(line1, paddingX + shadowDx, baseY + shadowDy, shadowPaint)
    c.drawText(line1, paddingX, baseY, bluePaint)
    val y2 = baseY + lineH + gap
    // 第二行：阴影 + 白字
    c.drawText(line2, paddingX + shadowDx, y2 + shadowDy, shadowPaint)
    c.drawText(line2, paddingX, y2, whitePaint)
    return bmp
}

/** 将水印合成到图片右下角（留边距） */
private fun addWatermarkToImage(src: Bitmap, badge: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawBitmap(src, 0f, 0f, null)
    val margin = (min(src.width, src.height) * 0.03f).roundToInt()
    val left = (src.width - badge.width - margin).coerceAtLeast(0)
    val top = (src.height - badge.height - margin).coerceAtLeast(0)
    c.drawBitmap(badge, left.toFloat(), top.toFloat(), null)
    return out
}

/** 通过 MediaStore 把图片写入 DCIM/Dalanben，直接进入系统相册 */
private suspend fun saveImageToGallery(context: Context, bmp: Bitmap, displayName: String): Uri? =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/$GALLERY_ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val coll = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(coll, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                ?: throw IOException("无法写入")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return@withContext uri
        } catch (_: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return@withContext null
        }
    }

/** 通过 MediaStore 把视频写入 DCIM/Dalanben，直接进入系统相册 */
private suspend fun saveVideoToGallery(context: Context, file: File, displayName: String): Uri? =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.SIZE, file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/$GALLERY_ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val coll = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(coll, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("无法写入")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return@withContext uri
        } catch (_: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return@withContext null
        }
    }

/** 粗略判断文件是否为 GIF（避免把动图压成静帧水印） */
private fun isLikelyGif(file: File): Boolean = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    opts.outMimeType?.contains("gif", ignoreCase = true) == true
} catch (_: Exception) {
    false
}
