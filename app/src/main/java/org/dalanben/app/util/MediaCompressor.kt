package org.dalanben.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ------------------------------------------------------------------
 * 统一的上传前压缩工具
 *  - 图片: 压到 600KB 以内 (最长边 1920)
 *  - 视频: 转码为 720p / 25fps / 50MB 以内, 过长则提示分段上传
 * ------------------------------------------------------------------ */

/** 图片压缩目标大小: 600KB */
const val IMAGE_MAX_BYTES = 600 * 1024

/** 图片最长边 */
const val IMAGE_MAX_EDGE = 1920

/** 长图判定: 高 >= 宽 * 该倍数 且 高 >= 该像素值才切片 (避免误伤普通竖图) */
const val LONG_IMAGE_RATIO = 3.0f
const val LONG_IMAGE_MIN_HEIGHT = 2000

/** 长图切片: 缩小后宽度上限 */
const val LONG_IMAGE_MAX_WIDTH = 1080

/** 长图切片: 每张切片(原始像素)最大高度 */
const val LONG_IMAGE_SLICE_HEIGHT = 2000

/** 视频短边目标 720p */
const val VIDEO_SHORT_SIDE = 720

/** 视频目标帧率 */
const val VIDEO_FPS = 25

/** 视频压缩目标大小: 50MB */
const val VIDEO_MAX_BYTES = 50L * 1024 * 1024

/** 视频最大时长: 超过则提示用户分段上传 */
const val VIDEO_MAX_DURATION_MS = 10 * 60 * 1000L

/** 音频压缩目标码率: 64kbps (平衡音质与体积) */
const val AUDIO_TARGET_BPS = 64_000

/** 音频上传最大体积: 超过此值才压缩 */
const val AUDIO_MAX_BYTES = 1024L * 1024

/**
 * 压缩结果。
 * @param file    成功时的输出文件
 * @param error   失败原因(给用户看的文案)
 * @param needSplit 视频过长, 需要用户自行剪辑后分多条上传
 */
data class MediaResult(
    val file: File? = null,
    val error: String? = null,
    val needSplit: Boolean = false,
)

/* ============================== 图片 ============================== */

/**
 * 读取 uri 图片并压缩到 [maxBytes] 以内, 输出 JPEG 缓存文件。
 * GIF 保留原样(避免动图被压成静态图)。
 */
suspend fun compressImage(
    context: Context,
    uri: Uri,
    prefix: String,
    maxBytes: Int = IMAGE_MAX_BYTES,
): MediaResult = withContext(Dispatchers.IO) {
    try {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.contains("gif", ignoreCase = true)) {
            val f = copyUriToCacheFile(context, uri, prefix, "gif")
            return@withContext if (f != null) MediaResult(f) else MediaResult(error = "读取图片失败")
        }

        // 1) 只读边界, 拿到原始宽高
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext MediaResult(error = "无法读取图片")
        }

        // 2) 采样解码, 先把超大图降到可控范围, 避免 OOM
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > IMAGE_MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return@withContext MediaResult(error = "图片解码失败")

        // 3) EXIF 方向校正 (否则手机竖拍图会躺倒)
        bmp = applyExifRotation(context, uri, bmp)

        // 4) 限制最长边
        bmp = scaleToMaxEdge(bmp, IMAGE_MAX_EDGE)

        // 5) 质量递减 + 必要时继续缩放, 压到目标体积
        val bytes = compressBitmapToBytes(bmp, maxBytes)
        bmp.recycle()

        val out = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { it.write(bytes) }
        MediaResult(out)
    } catch (e: OutOfMemoryError) {
        MediaResult(error = "图片过大, 内存不足")
    } catch (e: Exception) {
        MediaResult(error = e.message ?: "图片处理失败")
    }
}

/** 把已解码的 Bitmap(例如裁剪结果)压缩落盘, 同样限制在 [maxBytes] 以内。 */
suspend fun compressBitmapToFile(
    context: Context,
    bmp: Bitmap,
    prefix: String,
    maxBytes: Int = IMAGE_MAX_BYTES,
): MediaResult = withContext(Dispatchers.IO) {
    try {
        val scaled = scaleToMaxEdge(bmp, IMAGE_MAX_EDGE)
        val bytes = compressBitmapToBytes(scaled, maxBytes)
        scaled.recycle()
        val out = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { it.write(bytes) }
        MediaResult(out)
    } catch (e: Exception) {
        MediaResult(error = e.message ?: "图片处理失败")
    }
}

private fun compressBitmapToBytes(src: Bitmap, maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    var quality = 90
    while (true) {
        out.reset()
        src.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (out.size() <= maxBytes || quality <= 40) break
        quality -= 8
    }
    // 仍然超标: 逐步缩小尺寸(最多 5 轮)
    var work: Bitmap? = null
    var guard = 0
    while (out.size() > maxBytes && guard < 5) {
        val base = work ?: src
        val nw = (base.width * 0.8f).roundToInt().coerceAtLeast(1)
        val nh = (base.height * 0.8f).roundToInt().coerceAtLeast(1)
        val next = Bitmap.createScaledBitmap(base, nw, nh, true)
        if (work != null && work !== next) work.recycle()
        work = next
        out.reset()
        work.compress(Bitmap.CompressFormat.JPEG, 70, out)
        guard++
    }
    work?.recycle()
    return out.toByteArray()
}

private fun applyExifRotation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
    return try {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bmp
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated !== bmp) bmp.recycle()
        rotated
    } catch (_: Exception) {
        bmp
    }
}

private fun scaleToMaxEdge(bmp: Bitmap, maxEdge: Int): Bitmap {
    val longest = max(bmp.width, bmp.height)
    if (longest <= maxEdge) return bmp
    val ratio = maxEdge.toFloat() / longest
    val nw = (bmp.width * ratio).roundToInt().coerceAtLeast(1)
    val nh = (bmp.height * ratio).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
    if (scaled !== bmp) bmp.recycle()
    return scaled
}

/**
 * 长图自动切片: 当图片明显为竖向长图(高 >= 宽*3 且 高 >= 2000px)时,
 * 按 [LONG_IMAGE_SLICE_HEIGHT] 像素为若干张 JPEG 切片, 返回 file:// Uri 列表。
 *
 * 使用 [BitmapRegionDecoder] 分块解码, 不会把整张长图一次性载入内存, 避免 OOM。
 * 任何异常/非长图都返回空列表(调用方会回退为直接上传原图)。
 *
 * @param prefix 缓存文件名前缀
 */
suspend fun splitLongImage(
    context: Context,
    uri: Uri,
    prefix: String,
): List<Uri> = withContext(Dispatchers.IO) {
    var decoder: BitmapRegionDecoder? = null
    try {
        // 1) 先用输入流探测尺寸并判断是否为长图
        val probe = context.contentResolver.openInputStream(uri)
        val meta = probe?.use {
            val d = BitmapRegionDecoder.newInstance(it, false)
                ?: return@withContext emptyList()
            decoder = d
            Pair(d.width, d.height)
        } ?: return@withContext emptyList()

        val (w, h) = meta
        val isLong = h >= w * LONG_IMAGE_RATIO && h >= LONG_IMAGE_MIN_HEIGHT
        if (!isLong || w <= 0 || h <= 0) {
            decoder?.recycle()
            decoder = null
            return@withContext emptyList()
        }

        // 2) 计算整体缩放, 让切片宽度不超过 LONG_IMAGE_MAX_WIDTH
        val sample = max(1, (w.toFloat() / LONG_IMAGE_MAX_WIDTH).roundToInt())
        val scaledW = max(1, w / sample)
        val out = mutableListOf<Uri>()
        var top = 0
        var index = 0
        while (top < h) {
            val bottom = min(h, top + LONG_IMAGE_SLICE_HEIGHT)
            val rect = Rect(0, top, w, bottom)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val slice = decoder!!.decodeRegion(rect, opts) ?: break
            try {
                // 3) 切片压缩落盘; 长图单张也应控制在 600KB 内, 不足则降质
                val bytes = compressBitmapToBytes(slice, IMAGE_MAX_BYTES)
                val file = File(
                    context.cacheDir,
                    "${prefix}_slice_${System.currentTimeMillis()}_$index.jpg"
                )
                file.outputStream().use { it.write(bytes) }
                if (file.length() > 0) out.add(Uri.fromFile(file))
            } finally {
                slice.recycle()
            }
            index++
            top = bottom
        }
        out
    } catch (_: Exception) {
        emptyList()
    } finally {
        try { decoder?.recycle() } catch (_: Exception) {}
    }
}

/* ============================== 视频 ============================== */

private data class VideoInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val sizeBytes: Long,
)

/**
 * 视频转码: 短边 720p / 25fps / 目标 50MB 以内。
 *
 * - 时长超过 [VIDEO_MAX_DURATION_MS] 时不转码, 返回 needSplit=true 让调用方提示分段上传
 * - 原视频本身已达标(≤50MB 且短边 ≤720)时直接复用, 避免二次损失画质
 * - 转码失败时若原文件不超限则回退为原文件
 *
 * @param onProgress 转码进度 0..100
 */
suspend fun compressVideo(
    context: Context,
    uri: Uri,
    prefix: String,
    onProgress: (Int) -> Unit,
): MediaResult {
    val info = withContext(Dispatchers.IO) { probeVideo(context, uri) }
        ?: return MediaResult(error = "无法读取视频信息")

    if (info.durationMs > VIDEO_MAX_DURATION_MS) {
        val mins = VIDEO_MAX_DURATION_MS / 60000
        return MediaResult(
            error = "视频时长 ${formatDuration(info.durationMs)}，超过 ${mins} 分钟上限。" +
                "请把视频剪辑成多段后分多条上传。",
            needSplit = true,
        )
    }

    // 已达标: 直接复用原文件
    val shortSide = if (info.width > 0 && info.height > 0) min(info.width, info.height) else 0
    if (info.sizeBytes in 1..VIDEO_MAX_BYTES && shortSide in 1..VIDEO_SHORT_SIDE) {
        onProgress(100)
        val f = withContext(Dispatchers.IO) { copyUriToCacheFile(context, uri, prefix, "mp4") }
        return if (f != null) MediaResult(f) else MediaResult(error = "读取视频失败")
    }

    // 目标分辨率: 短边压到 720, 保持宽高比, 且宽高必须为偶数(编码器要求)
    var tw = info.width
    var th = info.height
    if (shortSide > VIDEO_SHORT_SIDE) {
        val ratio = VIDEO_SHORT_SIDE.toFloat() / shortSide
        tw = (tw * ratio).roundToInt()
        th = (th * ratio).roundToInt()
    }
    tw = (tw / 2) * 2
    th = (th / 2) * 2

    val durSec = max(1.0, info.durationMs / 1000.0)
    // 预留 15% 给容器开销和音频波动
    val budgetBits = VIDEO_MAX_BYTES * 8 * 0.85
    var videoBps = ((budgetBits / durSec) - AUDIO_BPS).toInt().coerceIn(400_000, 3_000_000)

    var lastError: String? = null
    var output: File? = null

    // 最多两轮: 第一轮按预算码率, 若仍超标则按实际结果回算码率再来一次
    for (attempt in 0 until 2) {
        val out = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.mp4")
        val err = runTransform(context, uri, out, tw, th, videoBps, onProgress)
        if (err != null) {
            lastError = err
            out.delete()
            break
        }
        if (!out.exists() || out.length() <= 0L) {
            lastError = "转码输出为空"
            out.delete()
            break
        }
        output?.delete()
        output = out
        if (out.length() <= VIDEO_MAX_BYTES) break

        // 超标: 按实际体积回算码率再压一轮
        val shrink = (VIDEO_MAX_BYTES.toDouble() / out.length()) * 0.9
        val next = (videoBps * shrink).toInt().coerceAtLeast(300_000)
        if (next >= videoBps) break
        videoBps = next
    }

    if (output != null && output.exists() && output.length() > 0L) return MediaResult(output)

    // 转码失败: 原文件不超限就直接用原文件兜底
    if (info.sizeBytes in 1..VIDEO_MAX_BYTES) {
        val f = withContext(Dispatchers.IO) { copyUriToCacheFile(context, uri, prefix, "mp4") }
        if (f != null) return MediaResult(f)
    }
    return MediaResult(error = lastError ?: "视频压缩失败, 请换一个视频重试")
}

private const val AUDIO_BPS = 96_000

/** 执行一次转码, 返回 null 表示成功, 否则返回错误信息。 */
private suspend fun runTransform(
    context: Context,
    uri: Uri,
    out: File,
    targetW: Int,
    targetH: Int,
    videoBps: Int,
    onProgress: (Int) -> Unit,
): String? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine<String?> { cont ->
        val handler = Handler(Looper.getMainLooper())
        val holder = ProgressHolder()
        var transformer: Transformer? = null
        var poll: Runnable? = null

        poll = Runnable {
            val t = transformer
            if (t != null && cont.isActive) {
                try {
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress.coerceIn(0, 100))
                    }
                } catch (_: Exception) {
                    // 忽略进度查询异常, 不影响转码本身
                }
                poll?.let { handler.postDelayed(it, 400L) }
            }
        }

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                poll?.let { handler.removeCallbacks(it) }
                onProgress(100)
                if (cont.isActive) cont.resume(null)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                poll?.let { handler.removeCallbacks(it) }
                val msg = exportException.message ?: "视频转码失败"
                if (cont.isActive) cont.resume(msg)
            }
        }

        val videoEffects = mutableListOf<Effect>()
        if (targetW > 0 && targetH > 0) {
            videoEffects.add(
                Presentation.createForWidthAndHeight(targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT)
            )
        }
        videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(VIDEO_FPS.toFloat()))

        try {
            val t = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder().setBitrate(videoBps).build()
                        )
                        .setEnableFallback(true)
                        .build()
                )
                .addListener(listener)
                .build()
            transformer = t

            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
                .build()

            cont.invokeOnCancellation {
                poll?.let { handler.removeCallbacks(it) }
                try { t.cancel() } catch (_: Exception) {}
            }

            t.start(item, out.absolutePath)
            poll?.let { handler.postDelayed(it, 400L) }
        } catch (e: Exception) {
            poll?.let { handler.removeCallbacks(it) }
            if (cont.isActive) cont.resume(e.message ?: "转码启动失败")
        }
    }
}

private fun probeVideo(context: Context, uri: Uri): VideoInfo? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        if (rotation == 90 || rotation == 270) {
            val tmp = w; w = h; h = tmp
        }
        val size = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }
        VideoInfo(w, h, duration, size)
    } catch (_: Exception) {
        null
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}分${s}秒" else "${s}秒"
}

/* ============================== 音频 ============================== */

/**
 * 音频处理: 不再做客户端转码(Media3 纯音频转码容易卡死, 且无进度/超时),
 * 直接复制原始文件并保留扩展名上传; 体积上限(10MB)由服务端校验。
 * 这样最稳妥: 不会出现转码卡死, 也不会因重编码损失音质。
 */
suspend fun compressAudio(
    context: Context,
    uri: Uri,
    prefix: String,
): MediaResult = withContext(Dispatchers.IO) {
    try {
        val ext = detectAudioExt(context, uri) ?: "mp3"
        val f = copyUriToCacheFile(context, uri, prefix, ext)
        if (f != null) MediaResult(f) else MediaResult(error = "读取音频失败")
    } catch (e: Exception) {
        MediaResult(error = e.message ?: "音频处理失败")
    }
}

/** 从 uri 的文件名推断音频扩展名; 不在白名单内则返回 null(交由调用方兜底为 mp3) */
private fun detectAudioExt(context: Context, uri: Uri): String? {
    return try {
        val name = context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val e = name?.substringAfterLast('.', "")?.lowercase()
        if (e in setOf("mp3", "m4a", "aac", "ogg", "wav", "flac")) e else null
    } catch (_: Exception) {
        null
    }
}

/* ============================== 公共 ============================== */

/** 原样拷贝 uri 到缓存文件(不做压缩)。 */
fun copyUriToCacheFile(context: Context, uri: Uri, prefix: String, ext: String): File? = try {
    val f = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        f.outputStream().use { output -> input.copyTo(output) }
    }
    if (f.length() > 0) f else null
} catch (_: Exception) {
    null
}

/** 人类可读的文件体积。 */
fun humanSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0fKB", bytes / 1024.0)
    else -> "${bytes}B"
}
